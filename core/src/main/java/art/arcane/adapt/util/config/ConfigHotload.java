package art.arcane.adapt.util.config;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptEnvironment;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.content.gui.ConfigGui;
import art.arcane.adapt.content.gui.SkillsGui;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RuntimeMessages;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.fx.SoundCategory;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.inventorygui.GuiSessions;
import art.arcane.adapt.util.common.inventorygui.GuiWindow;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.director.context.AdaptationListingHandler;
import art.arcane.volmlib.util.hotload.ConfigHotloadEngine;
import com.google.gson.JsonElement;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

/**
 * Watches adapt.toml, models.toml, the skill and adaptation configs and the language files, reloads whatever changed on
 * the global server context and reopens the Adapt menus players have open. Writes Adapt makes itself go through
 * {@link #write(File, ConfigHotloadEngine.FileWrite)} so they are not reloaded back.
 */
public final class ConfigHotload {
  private static final long WATCHER_POLL_MS = 500L;
  private static final long HOTLOAD_COOLDOWN_MS = 3_000L;
  private static final int MAX_DIFF_MESSAGES_PER_FILE = 12;
  private static final int MAX_HOTLOAD_CONFIG_BYTES = 3 * 1024 * 1024;
  private static final long HOTLOAD_IO_SHUTDOWN_MILLIS = 2_000L;

  private final AtomicBoolean hotloadPollInFlight = new AtomicBoolean();
  private final AtomicLong hotloadGeneration = new AtomicLong();
  private final ConfigHotloadEngine hotloadEngine = new ConfigHotloadEngine(
      this::isManagedConfigFile,
      this::listKnownConfigFiles,
      this::readFileContent,
      this::normalizeContent
  );
  private TickedObject configTicker;
  private ExecutorService hotloadIo;
  private File adaptConfigFile;
  private File modelsFile;
  private File skillsFolder;
  private File adaptationsFolder;
  private File localeLanguageFolder;

  /** The running core's hotload, or null when Adapt is not running. */
  public static ConfigHotload current() {
    AdaptCore core = AdaptCore.current();
    return core == null ? null : core.getHotload();
  }

  /** Writes through the running hotload so the watcher does not reload the write; writes directly when none runs. */
  public static <T> T writeTracked(File file, ConfigHotloadEngine.FileWrite<T> writer) throws Exception {
    ConfigHotload hotload = current();
    return hotload == null ? writer.write().value() : hotload.write(file, writer);
  }

  public static void noteTrackedWrite(File file, String content) {
    ConfigHotload hotload = current();
    if (hotload != null) {
      hotload.noteSelfWrite(file, content);
    }
  }

  public static void refreshLocalization() {
    ConfigHotload hotload = current();
    if (hotload != null) {
      hotload.refreshLocalizationConsumers();
    }
  }

  public void start() {
    adaptConfigFile = AdaptEnvironment.dataFile("adapt.toml");
    modelsFile = AdaptEnvironment.dataFile("models.toml");
    skillsFolder = dataFolder("skills");
    adaptationsFolder = dataFolder("adaptations");
    localeLanguageFolder = AdaptLanguage.languageFolder();
    hotloadEngine.configure(
        WATCHER_POLL_MS,
        HOTLOAD_COOLDOWN_MS,
        List.of(adaptConfigFile, modelsFile),
        List.of(skillsFolder, adaptationsFolder, localeLanguageFolder)
    );
    hotloadGeneration.incrementAndGet();
    hotloadIo = Executors.newSingleThreadExecutor((Runnable task) -> {
      Thread thread = new Thread(task, "Adapt-Config-Hotload-IO");
      thread.setDaemon(true);
      return thread;
    });
    AdaptLog.info("Config hotload watcher enabled for Adapt configs and language files.");

    configTicker = new TickedObject("config", "config-hotload-service", WATCHER_POLL_MS) {
      @Override
      public void onTick() {
        queueConfigPoll();
      }
    };
    configTicker.activateRuntime();
  }

  public void stop() {
    if (configTicker != null) {
      configTicker.unregister();
      configTicker = null;
    }
    hotloadGeneration.incrementAndGet();
    ExecutorService active = hotloadIo;
    hotloadIo = null;
    if (active != null) {
      active.shutdownNow();
      try {
        if (!active.awaitTermination(HOTLOAD_IO_SHUTDOWN_MILLIS, TimeUnit.MILLISECONDS)) {
          AdaptLog.warn("Config hotload IO worker did not stop within two seconds.");
        }
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        AdaptLog.error(interrupted);
      }
    }
    hotloadPollInFlight.set(false);
    hotloadEngine.clear();
  }

  public void refreshLocalizationConsumers() {
    SkillRegistry registry = skillRegistry();
    if (registry != null) {
      registry.synchronizeAdvancementRuntime();
    }
    refreshOpenAdaptGuis();
  }

  public <T> T write(File file, ConfigHotloadEngine.FileWrite<T> writer) throws Exception {
    return hotloadEngine.write(file, writer);
  }

  public void noteSelfWrite(File file, String content) {
    hotloadEngine.noteSelfWrite(file, content);
  }

  private static File dataFolder(String name) {
    Path directory = AdaptEnvironment.dataDirectory();
    if (directory == null) {
      throw new IllegalStateException("Adapt data directory is not available");
    }
    File folder = directory.resolve(name).toFile();
    folder.mkdirs();
    return folder;
  }

  private void queueConfigPoll() {
    ExecutorService active = hotloadIo;
    if (active == null || !hotloadPollInFlight.compareAndSet(false, true)) {
      return;
    }
    long generation = hotloadGeneration.get();
    try {
      active.execute(() -> pollConfigChanges(generation));
    } catch (RejectedExecutionException rejected) {
      hotloadPollInFlight.set(false);
    }
  }

  private void pollConfigChanges(long generation) {
    boolean handedOff = false;
    try {
      List<ConfigHotloadEngine.StableContentSnapshot> touched = pollConfigSnapshots();
      if (touched.isEmpty() || generation != hotloadGeneration.get()) {
        return;
      }
      AdaptScheduler scheduler = Tasks.scheduler();
      if (scheduler == null || !scheduler.runGlobal(() -> applyConfigChanges(generation, touched))) {
        retainSnapshotsForRetry(touched);
        AdaptLog.warn("Config hotload could not reach the global server context; the batch remains queued.");
        return;
      }
      handedOff = true;
    } catch (Throwable failure) {
      AdaptLog.warn("Config hotload filesystem scan failed: " + failure.getMessage());
      AdaptLog.error(failure);
    } finally {
      if (!handedOff) {
        hotloadPollInFlight.set(false);
      }
    }
  }

  private List<ConfigHotloadEngine.StableContentSnapshot> pollConfigSnapshots() {
    List<ConfigHotloadEngine.StableContentSnapshot> touched = new ArrayList<>(hotloadEngine.pollTouchedSnapshots());
    touched.sort(Comparator
        .comparingInt(this::hotloadPriority)
        .thenComparing((ConfigHotloadEngine.StableContentSnapshot snapshot) -> snapshot.file().getAbsolutePath()));
    return touched;
  }

  private void applyConfigChanges(long generation, List<ConfigHotloadEngine.StableContentSnapshot> touched) {
    try {
      if (generation != hotloadGeneration.get()) {
        return;
      }

      boolean refreshedSomething = false;
      for (ConfigHotloadEngine.StableContentSnapshot snapshot : touched) {
        refreshedSomething = processConfigChange(snapshot) || refreshedSomething;
      }

      if (refreshedSomething) {
        refreshOpenAdaptGuis();
      }
    } finally {
      hotloadPollInFlight.set(false);
    }
  }

  private void retainSnapshotsForRetry(List<ConfigHotloadEngine.StableContentSnapshot> snapshots) {
    for (ConfigHotloadEngine.StableContentSnapshot snapshot : snapshots) {
      hotloadEngine.retrySnapshot(snapshot);
    }
  }

  private boolean processConfigChange(ConfigHotloadEngine.StableContentSnapshot snapshot) {
    File file = snapshot.file();
    if ("missing".equals(snapshot.signature()) && !isLocaleLanguageFile(file)) {
      hotloadEngine.processSnapshotChange(snapshot, (ConfigHotloadEngine.StableContentSnapshot ignored) -> true, null);
      AdaptLog.warn("Config was removed; retaining the last valid runtime state without recreating " + file.getPath() + ".");
      return false;
    }

    return hotloadEngine.processSnapshotChange(snapshot, this::applyConfigChange, (ConfigHotloadEngine.ContentDelta delta) -> {
      if (isModelsConfigFile(file)) {
        return;
      }

      notifyOps(file, delta.before(), delta.after());
    });
  }

  private boolean applyConfigChange(ConfigHotloadEngine.StableContentSnapshot snapshot) {
    File file = snapshot.file();
    String raw = snapshot.normalizedContent();
    try {
      if (isAdaptConfigFile(file)) {
        boolean ok = AdaptConfig.reloadSnapshot(raw, file);
        if (ok) {
          refreshGlobalRuntimeSettings();
        } else {
          AdaptLog.warn("Skipped hotload for " + file.getPath() + " due to invalid config.");
        }
        return ok;
      }

      if (isLocaleLanguageFile(file)) {
        boolean ok = AdaptLanguage.reloadLanguageSnapshot(file, raw);
        SkillRegistry registry = skillRegistry();
        if (ok && registry != null) {
          registry.synchronizeAdvancementRuntime();
        }
        return ok;
      }

      if (isSkillConfigFile(file)) {
        return reloadSkillConfig(file, raw);
      }

      if (isAdaptationConfigFile(file)) {
        return reloadAdaptationConfig(file, raw);
      }

      if (isModelsConfigFile(file)) {
        return reloadModelsConfig(file, raw);
      }

      return validateConfig(raw, file);
    } catch (Throwable e) {
      AdaptLog.warn("Skipped hotload for " + file.getPath() + " due to invalid config: " + e.getMessage());
      AdaptLog.error(e);
      return false;
    }
  }

  private boolean reloadSkillConfig(File file, String raw) {
    String skillName = toConfigName(file.getName());
    SkillRegistry registry = skillRegistry();
    if (skillName == null || registry == null) {
      return false;
    }

    boolean ok = registry.hotReloadSkillConfig(skillName, raw, file);
    if (ok) {
      AdaptationListingHandler.initializeAdaptationListings();
    } else {
      AdaptLog.warn("Skipped hotload for " + file.getPath() + " due to invalid skill config.");
    }
    return ok;
  }

  private boolean reloadAdaptationConfig(File file, String raw) {
    String adaptationName = toConfigName(file.getName());
    SkillRegistry registry = skillRegistry();
    if (adaptationName == null || registry == null) {
      return false;
    }

    for (Skill<?> skill : registry.getAllSkills()) {
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        if (!adaptation.getName().equalsIgnoreCase(adaptationName)) {
          continue;
        }

        boolean ok = registry.hotReloadAdaptationConfig(adaptationName, raw, file);
        if (ok) {
          AdaptationListingHandler.initializeAdaptationListings();
        } else {
          AdaptLog.warn("Skipped hotload for " + file.getPath() + " due to invalid adaptation config.");
        }
        return ok;
      }
    }

    return validateConfig(raw, file);
  }

  private boolean reloadModelsConfig(File file, String raw) {
    return CustomModel.reloadSnapshot(raw, file);
  }

  private void refreshGlobalRuntimeSettings() {
    String previousLocale = AdaptLanguage.activeLocale();
    if (AdaptLanguage.reloadPassive() && !previousLocale.equals(AdaptLanguage.activeLocale())) {
      AdaptLanguage.requestConfiguredLocale();
    }

    port().coreConfigReloaded();

    if (AdaptConfig.get().isCustomModels()) {
      CustomModel.reloadFromDiskPassive();
    } else {
      CustomModel.clear();
    }

    SkillRegistry registry = skillRegistry();
    if (registry != null) {
      registry.synchronizeAdvancementRuntime();
    }
  }

  private boolean validateConfig(String raw, File file) {
    try {
      return parseStructured(raw, file) != null;
    } catch (Throwable e) {
      AdaptLog.error(e);
      return false;
    }
  }

  private int hotloadPriority(ConfigHotloadEngine.StableContentSnapshot snapshot) {
    File file = snapshot.file();
    if (isAdaptConfigFile(file)) {
      return 0;
    }
    if (isSkillConfigFile(file)) {
      return 1;
    }
    if (isAdaptationConfigFile(file)) {
      return 2;
    }
    if (isModelsConfigFile(file)) {
      return 3;
    }
    return 4;
  }

  private boolean isAdaptConfigFile(File file) {
    return sameFile(file, adaptConfigFile);
  }

  private boolean isModelsConfigFile(File file) {
    return sameFile(file, modelsFile);
  }

  private boolean isSkillConfigFile(File file) {
    return isDirectChild(skillsFolder, file) && ConfigFileSupport.isTomlFile(file);
  }

  private boolean isAdaptationConfigFile(File file) {
    return isDirectChild(adaptationsFolder, file) && ConfigFileSupport.isTomlFile(file);
  }

  private boolean isLocaleLanguageFile(File file) {
    return isDirectChild(localeLanguageFolder, file)
        && AdaptLanguage.isLanguageFile(file);
  }

  private boolean isManagedConfigFile(File file) {
    return !isTemporaryArtifact(file)
        && (isAdaptConfigFile(file)
        || isModelsConfigFile(file)
        || isSkillConfigFile(file)
        || isAdaptationConfigFile(file)
        || isLocaleLanguageFile(file));
  }

  private boolean isTemporaryArtifact(File file) {
    if (file == null) {
      return false;
    }
    String name = file.getName().toLowerCase(Locale.ROOT);
    return name.startsWith(".")
        || name.startsWith("~")
        || name.startsWith("#")
        || name.endsWith("~")
        || name.contains(".tmp.")
        || name.contains(".temp.")
        || name.contains(".part.")
        || name.contains(".swp.")
        || name.contains(".swx.")
        || name.contains(".bak.");
  }

  private boolean isDirectChild(File parent, File child) {
    if (parent == null || child == null) {
      return false;
    }

    File childParent = child.getParentFile();
    return childParent != null && sameFile(parent, childParent);
  }

  private boolean sameFile(File a, File b) {
    return a != null && b != null && a.getAbsoluteFile().equals(b.getAbsoluteFile());
  }

  private String toConfigName(String fileName) {
    return ConfigFileSupport.configNameFromFileName(fileName);
  }

  private List<File> listKnownConfigFiles() {
    List<File> files = new ArrayList<>();
    Map<String, File> added = new HashMap<>();

    addIfManaged(files, added, adaptConfigFile);
    addIfManaged(files, added, modelsFile);

    addDirectChildren(skillsFolder, files, added);
    addDirectChildren(adaptationsFolder, files, added);
    addDirectChildren(localeLanguageFolder, files, added);

    return files;
  }

  private void addDirectChildren(File folder, List<File> out, Map<String, File> added) {
    if (folder == null || !folder.exists() || !folder.isDirectory()) {
      return;
    }

    File[] children = folder.listFiles();
    if (children == null || children.length == 0) {
      return;
    }

    for (File child : children) {
      if (child == null || !child.isFile()) {
        continue;
      }
      addIfManaged(out, added, child);
    }
  }

  private void addIfManaged(List<File> out, Map<String, File> added, File file) {
    if (file == null || !isManagedConfigFile(file)) {
      return;
    }

    String path = file.getAbsolutePath();
    if (added.putIfAbsent(path, file) != null) {
      return;
    }

    out.add(file);
  }

  private String readFileContent(File file) {
    if (file == null || !file.exists() || !file.isFile()) {
      return null;
    }

    try (InputStream input = Files.newInputStream(file.toPath())) {
      byte[] content = input.readNBytes(MAX_HOTLOAD_CONFIG_BYTES + 1);
      if (content.length > MAX_HOTLOAD_CONFIG_BYTES) {
        throw new IOException("Config exceeds " + MAX_HOTLOAD_CONFIG_BYTES + " bytes: " + file);
      }
      return new String(content, StandardCharsets.UTF_8);
    } catch (IOException failure) {
      throw new UncheckedIOException("Failed to capture hotload content from " + file, failure);
    }
  }

  private String normalizeContent(String text) {
    if (text == null) {
      return null;
    }
    return ConfigFileSupport.normalize(text);
  }

  private JsonElement parseStructured(String raw, File file) {
    if (raw == null || raw.isBlank()) {
      return null;
    }

    return ConfigFileSupport.parseToJsonElement(raw, file);
  }

  private void notifyOps(File file, String before, String after) {
    List<ConfigHotloadEngine.DiffEntry> diffs = ConfigHotloadEngine.computeStructuredDiff(
        before,
        after,
        (String raw) -> parseStructured(raw, null)
    );
    if (diffs.isEmpty()) {
      return;
    }

    String relative = relativizeToDataFolder(file);
    List<String> messages = new ArrayList<>();
    int shown = Math.min(MAX_DIFF_MESSAGES_PER_FILE, diffs.size());
    for (int i = 0; i < shown; i++) {
      ConfigHotloadEngine.DiffEntry diff = diffs.get(i);
      messages.add(formatHotloadMessage(relative, diff.key(), diff.oldValue(), diff.newValue()));
    }

    if (diffs.size() > shown) {
      int remaining = diffs.size() - shown;
      messages.add(AdaptLanguage.text(
          RuntimeMessages.CONFIG_HOTLOAD_TRUNCATED,
          untrusted("file", relative),
          trusted("remaining", remaining)
      ));
    }

    Tasks.s(() -> {
      AdaptServer server = adaptServer();
      if (server == null) {
        return;
      }
      int refused = 0;
      for (PlayerRef player : server.getOnlinePlayerSnapshot()) {
        if (!scheduleOperatorNotification(player, messages, Sounds.BLOCK_NOTE_BLOCK_PLING)) {
          refused++;
        }
      }
      if (refused > 0) {
        AdaptLog.warn("Config hotload could not schedule operator notifications for " + refused + " online players.");
      }
    });
  }

  static boolean scheduleOperatorNotification(PlayerRef player, List<String> messages, Id sound) {
    return Tasks.runEntity(player, () -> {
      if (!player.isOp()) {
        return;
      }
      player.playSound(player.location(), sound, SoundCategory.MASTER, 0.8f, 1.6f);
      messages.forEach(player::sendMessage);
    });
  }

  private String formatHotloadMessage(String file, String key, String oldValue, String newValue) {
    return AdaptLanguage.text(
        RuntimeMessages.CONFIG_HOTLOADED,
        untrusted("file", file),
        untrusted("key", key),
        untrusted("oldValue", formatValue(oldValue)),
        untrusted("newValue", formatValue(newValue))
    );
  }

  private String formatValue(String value) {
    return ConfigHotloadEngine.compactValue(value, 120);
  }

  private String relativizeToDataFolder(File file) {
    try {
      return AdaptEnvironment.dataDirectory().relativize(file.toPath()).toString();
    } catch (Throwable e) {
      return file.getName();
    }
  }

  private void refreshOpenAdaptGuis() {
    Tasks.s(() -> {
      for (Map.Entry<UUID, GuiWindow> entry : GuiSessions.snapshot().entrySet()) {
        UUID playerId = entry.getKey();
        GuiWindow window = entry.getValue();
        PlayerRef player = onlinePlayer(playerId);
        if (isStaleGuiEntry(player, window)) {
          GuiSessions.untrack(playerId, window);
          continue;
        }

        reopenFromTag(player, window.getTag());
      }
    });
  }

  /** A menu drops visibility synchronously inside the close event, before its close callback runs. */
  static boolean isStaleGuiEntry(PlayerRef player, GuiWindow window) {
    if (player == null || window == null || !player.isOnline()) {
      return true;
    }

    if (!window.isVisible()) {
      return true;
    }

    PlayerRef viewer = window.getViewer();
    return viewer == null || !player.uuid().equals(viewer.uuid());
  }

  private void reopenFromTag(PlayerRef player, String tag) {
    if ("/".equals(tag)) {
      SkillsGui.open(player);
      return;
    }

    if (tag == null) {
      return;
    }

    if (tag.startsWith("config/")) {
      ConfigGui.reopenFromTag(player, tag);
      return;
    }

    if (!tag.startsWith("skill/")) {
      return;
    }

    String[] parts = tag.split("/");
    if (parts.length < 2) {
      SkillsGui.open(player);
      return;
    }

    SkillRegistry registry = skillRegistry();
    Skill<?> skill = registry == null ? null : registry.getSkill(parts[1]);
    if (skill == null || !skill.isEnabled()) {
      SkillsGui.open(player);
      return;
    }

    if (parts.length == 2) {
      skill.openGui(player);
      return;
    }

    String adaptationName = parts[2];
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (!adaptation.getName().equalsIgnoreCase(adaptationName)) {
        continue;
      }

      if (adaptation.isEnabled()) {
        adaptation.openGui(player);
      } else {
        skill.openGui(player);
      }
      return;
    }

    skill.openGui(player);
  }

  private static PlayerRef onlinePlayer(UUID playerId) {
    AdaptCore core = AdaptCore.current();
    return core == null ? null : core.getPlatform().host().player(playerId);
  }

  private static AdaptServer adaptServer() {
    AdaptCore core = AdaptCore.current();
    return core == null ? null : core.getAdaptServer();
  }

  private static SkillRegistry skillRegistry() {
    AdaptServer server = adaptServer();
    return server == null ? null : server.getSkillRegistry();
  }

  private static ConfigHotloadPort port() {
    return AdaptPorts.get(ConfigHotloadPort.class, ConfigHotloadPort.NONE);
  }
}
