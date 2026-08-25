package art.arcane.adapt.service;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.MutationManager;
import art.arcane.adapt.api.protection.ProtectorRegistry;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.content.gui.ConfigGui;
import art.arcane.adapt.content.gui.MutationGui;
import art.arcane.adapt.content.gui.SkillsGui;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.AdaptLanguageDownload;
import art.arcane.adapt.localization.catalog.RuntimeMessages;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.plugin.AdaptService;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigFileSupport;
import art.arcane.volmlib.util.hotload.ConfigHotloadEngine;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import com.google.gson.JsonElement;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

import static art.arcane.adapt.util.director.context.AdaptationListingHandler.initializeAdaptationListings;
import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

public class HotloadSVC implements AdaptService {
  private static final long WATCHER_POLL_MS = 500L;
  private static final long HOTLOAD_COOLDOWN_MS = 3_000L;
  private static final int MAX_DIFF_MESSAGES_PER_FILE = 12;
  private static final int MUTATION_RECONCILIATION_BATCH_SIZE = 32;
  private static final int MAX_HOTLOAD_CONFIG_BYTES = 2 * 1024 * 1024;
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
  private File mutationsConfigFile;
  private File skillsFolder;
  private File adaptationsFolder;
  private File localeOverrideFolder;
  @Override
  public void onEnable() {
    adaptConfigFile = Adapt.instance.getDataFile("adapt.toml");
    modelsFile = Adapt.instance.getDataFile("models.toml");
    mutationsConfigFile = Adapt.instance.getDataFile("mutations.toml");
    skillsFolder = Adapt.instance.getDataFolder("skills");
    adaptationsFolder = Adapt.instance.getDataFolder("adaptations");
    localeOverrideFolder = AdaptLanguage.overrideFolder();
    hotloadEngine.configure(
        WATCHER_POLL_MS,
        HOTLOAD_COOLDOWN_MS,
        List.of(adaptConfigFile, modelsFile, mutationsConfigFile),
        List.of(skillsFolder, adaptationsFolder, localeOverrideFolder)
    );
    hotloadGeneration.incrementAndGet();
    hotloadIo = Executors.newSingleThreadExecutor((Runnable task) -> {
      Thread thread = new Thread(task, "Adapt-Config-Hotload-IO");
      thread.setDaemon(true);
      return thread;
    });
    Adapt.info("Config hotload watcher enabled for Adapt configs and locale overrides.");

    configTicker = new TickedObject("config", "config-hotload-service", WATCHER_POLL_MS) {
      @Override
      public void onTick() {
        queueConfigPoll();
      }
    };
    configTicker.activateRuntime();
  }

  @Override
  public void onDisable() {
    if (configTicker != null) {
      configTicker.unregister();
      configTicker = null;
    }
    hotloadGeneration.incrementAndGet();
    ExecutorService current = hotloadIo;
    hotloadIo = null;
    if (current != null) {
      current.shutdownNow();
      try {
        if (!current.awaitTermination(HOTLOAD_IO_SHUTDOWN_MILLIS, TimeUnit.MILLISECONDS)) {
          Adapt.warn("Config hotload IO worker did not stop within two seconds.");
        }
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        Adapt.error(interrupted);
      }
    }
    hotloadPollInFlight.set(false);
    hotloadEngine.clear();
  }

  public void refreshLocalizationConsumers() {
    Adapt.instance.getAdaptServer().getSkillRegistry().synchronizeAdvancementRuntime();
    refreshOpenAdaptGuis();
  }

  private void queueConfigPoll() {
    ExecutorService current = hotloadIo;
    if (current == null || !hotloadPollInFlight.compareAndSet(false, true)) {
      return;
    }
    long generation = hotloadGeneration.get();
    try {
      current.execute(() -> pollConfigChanges(generation));
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
      if (!SchedulerUtils.runGlobal(Adapt.instance, () -> applyConfigChanges(generation, touched))) {
        retainSnapshotsForRetry(touched);
        Adapt.warn("Config hotload could not reach the global server context; the batch remains queued.");
        return;
      }
      handedOff = true;
    } catch (Throwable failure) {
      Adapt.warn("Config hotload filesystem scan failed: " + failure.getMessage());
      Adapt.error(failure);
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
        .thenComparing(snapshot -> snapshot.file().getAbsolutePath()));
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
      hotloadEngine.processSnapshotChange(snapshot, ignored -> false, null);
    }
  }

  private boolean processConfigChange(ConfigHotloadEngine.StableContentSnapshot snapshot) {
    File file = snapshot.file();
    if ("missing".equals(snapshot.signature())) {
      hotloadEngine.processSnapshotChange(snapshot, ignored -> true, null);
      Adapt.warn("Config was removed; retaining the last valid runtime state without recreating " + file.getPath() + ".");
      return false;
    }

    return hotloadEngine.processSnapshotChange(snapshot, this::applyConfigChange, delta -> {
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
          reconcileCurrentMutationQualification();
        } else {
          Adapt.warn("Skipped hotload for " + file.getPath() + " due to invalid config.");
        }
        return ok;
      }

      if (isLocaleOverrideFile(file)) {
        boolean ok = AdaptLanguage.reloadOverrideSnapshot(file, raw);
        if (ok) {
          Adapt.instance.getAdaptServer().getSkillRegistry().synchronizeAdvancementRuntime();
        }
        return ok;
      }

      if (isMutationsConfigFile(file)) {
        return reloadMutationsConfig(file, raw);
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
      Adapt.warn("Skipped hotload for " + file.getPath() + " due to invalid config: " + e.getMessage());
      Adapt.error(e);
      return false;
    }
  }

  private boolean reloadSkillConfig(File file, String raw) {
    String skillName = toConfigName(file.getName());
    if (skillName == null) {
      return false;
    }

    SkillRegistry registry = Adapt.instance.getAdaptServer().getSkillRegistry();
    boolean ok = registry.hotReloadSkillConfig(skillName, raw, file);
    if (ok) {
      initializeAdaptationListings();
      reconcileCurrentMutationQualification();
    } else {
      Adapt.warn("Skipped hotload for " + file.getPath() + " due to invalid skill config.");
    }
    return ok;
  }

  private boolean reloadAdaptationConfig(File file, String raw) {
    String adaptationName = toConfigName(file.getName());
    if (adaptationName == null) {
      return false;
    }

    for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        if (!adaptation.getName().equalsIgnoreCase(adaptationName)) {
          continue;
        }

        SkillRegistry registry = Adapt.instance.getAdaptServer().getSkillRegistry();
        boolean ok = registry.hotReloadAdaptationConfig(adaptationName, raw, file);
        if (ok) {
          initializeAdaptationListings();
          reconcileCurrentMutationQualification();
        } else {
          Adapt.warn("Skipped hotload for " + file.getPath() + " due to invalid adaptation config.");
        }
        return ok;
      }
    }

    return validateConfig(raw, file);
  }

  private boolean reloadModelsConfig(File file, String raw) {
    return CustomModel.reloadSnapshot(raw, file);
  }

  private boolean reloadMutationsConfig(File file, String raw) {
    MutationSVC mutationService = MutationSVC.get();
    if (mutationService == null || !mutationService.reloadSnapshot(raw, file)) {
      Adapt.warn("Skipped hotload for " + file.getPath() + " due to invalid Mutation config.");
      return false;
    }
    reconcileOnlineMutations(mutationService.getManager());
    return true;
  }

  public static void reconcileOnlineMutations(MutationManager manager) {
    if (manager == null || !manager.getConfig().isEnabled()) {
      return;
    }
    List<Player> players = new ArrayList<>(Adapt.instance.getAdaptServer().getOnlinePlayerSnapshot());
    for (int start = 0; start < players.size(); start += MUTATION_RECONCILIATION_BATCH_SIZE) {
      int from = start;
      int to = Math.min(players.size(), start + MUTATION_RECONCILIATION_BATCH_SIZE);
      int delay = start / MUTATION_RECONCILIATION_BATCH_SIZE;
      J.s(() -> {
        for (int index = from; index < to; index++) {
          Player player = players.get(index);
          if (player != null && player.isOnline()) {
            J.runEntity(player, () -> manager.reconcile(player));
          }
        }
      }, delay);
    }
  }

  private void reconcileCurrentMutationQualification() {
    MutationSVC mutationService = MutationSVC.get();
    if (mutationService != null) {
      reconcileOnlineMutations(mutationService.getManager());
    }
  }

  private void refreshGlobalRuntimeSettings() {
    AdaptLanguage.reloadPassive();
    AdaptLanguageDownload.requestConfiguredLocale();

    ProtectorRegistry protectorRegistry = Adapt.instance.getProtectorRegistry();
    if (protectorRegistry != null) {
      protectorRegistry.refreshDefaultProtectors();
    }

    if (AdaptConfig.get().isCustomModels()) {
      CustomModel.reloadFromDiskPassive();
    } else {
      CustomModel.clear();
    }

    Adapt.instance.getAdaptServer().getSkillRegistry().synchronizeAdvancementRuntime();
  }

  private boolean validateConfig(String raw, File file) {
    try {
      JsonElement parsed = parseStructured(raw, file);
      if (parsed == null) {
        return false;
      }

      if (ConfigFileSupport.isTomlFile(file)) {
        return true;
      }

      return true;
    } catch (Throwable e) {
      Adapt.error(e);
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
    if (isMutationsConfigFile(file)) {
      return 3;
    }
    if (isModelsConfigFile(file)) {
      return 4;
    }
    return 5;
  }

  private boolean isAdaptConfigFile(File file) {
    return sameFile(file, adaptConfigFile);
  }

  private boolean isModelsConfigFile(File file) {
    return sameFile(file, modelsFile);
  }

  private boolean isMutationsConfigFile(File file) {
    return sameFile(file, mutationsConfigFile);
  }

  private boolean isSkillConfigFile(File file) {
    return isDirectChild(skillsFolder, file) && ConfigFileSupport.isTomlFile(file);
  }

  private boolean isAdaptationConfigFile(File file) {
    return isDirectChild(adaptationsFolder, file) && ConfigFileSupport.isTomlFile(file);
  }

  private boolean isLocaleOverrideFile(File file) {
    return isDirectChild(localeOverrideFolder, file)
        && file.getName().toLowerCase(Locale.ROOT).endsWith(".toml");
  }

  private boolean isManagedConfigFile(File file) {
    return !isTemporaryArtifact(file)
        && (isAdaptConfigFile(file)
        || isModelsConfigFile(file)
        || isMutationsConfigFile(file)
        || isSkillConfigFile(file)
        || isAdaptationConfigFile(file)
        || isLocaleOverrideFile(file));
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
    addIfManaged(files, added, mutationsConfigFile);

    addDirectChildren(skillsFolder, files, added);
    addDirectChildren(adaptationsFolder, files, added);
    addDirectChildren(localeOverrideFolder, files, added);

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
        raw -> parseStructured(raw, null)
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

    J.s(() -> {
      int refused = 0;
      for (Player player : Adapt.instance.getAdaptServer().getOnlinePlayerSnapshot()) {
        if (!scheduleOperatorNotification(player, messages, Sound.BLOCK_NOTE_BLOCK_PLING)) {
          refused++;
        }
      }
      if (refused > 0) {
        Adapt.warn("Config hotload could not schedule operator notifications for " + refused + " online players.");
      }
    });
  }

  static boolean scheduleOperatorNotification(Player player, List<String> messages, Sound sound) {
    return J.runEntity(player, () -> {
      if (!player.isOp()) {
        return;
      }
      player.playSound(player.getLocation(), sound, 0.8f, 1.6f);
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
      return Adapt.instance.getDataFolder().toPath().relativize(file.toPath()).toString();
    } catch (Throwable e) {
      return file.getName();
    }
  }

  private void refreshOpenAdaptGuis() {
    J.s(() -> {
      Map<String, UIWindow> open = new HashMap<>(Adapt.instance.getGuiLeftovers());
      for (Map.Entry<String, UIWindow> entry : open.entrySet()) {
        String playerKey = entry.getKey();
        UIWindow window = entry.getValue();
        if (window == null) {
          Adapt.instance.getGuiLeftovers().remove(playerKey);
          continue;
        }

        UUID uuid = parsePlayerKey(playerKey);
        if (uuid == null) {
          Adapt.instance.getGuiLeftovers().remove(playerKey, window);
          continue;
        }

        Player player = Bukkit.getPlayer(uuid);
        if (isStaleGuiEntry(player, window)) {
          Adapt.instance.getGuiLeftovers().remove(playerKey, window);
          continue;
        }

        reopenFromTag(player, window.getTag());
      }
    });
  }

  private static UUID parsePlayerKey(String playerKey) {
    try {
      return UUID.fromString(playerKey);
    } catch (Throwable ignored) {
      return null;
    }
  }

  /** UIWindow drops visibility synchronously inside the close event, a tick before its close callback runs. */
  static boolean isStaleGuiEntry(Player player, UIWindow window) {
    if (player == null || window == null || !player.isOnline()) {
      return true;
    }

    if (!window.isVisible()) {
      return true;
    }

    Player viewer = window.getViewer();
    return viewer == null || !player.getUniqueId().equals(viewer.getUniqueId());
  }

  private void reopenFromTag(Player player, String tag) {
    if (tag == null || tag.isBlank() || "/".equals(tag)) {
      SkillsGui.open(player);
      return;
    }

    if (tag.startsWith("config/")) {
      ConfigGui.reopenFromTag(player, tag);
      return;
    }

    if (tag.startsWith("mutations")) {
      MutationGui.reopenFromTag(player, tag);
      return;
    }

    if (!tag.startsWith("skill/")) {
      SkillsGui.open(player);
      return;
    }

    String[] parts = tag.split("/");
    if (parts.length < 2) {
      SkillsGui.open(player);
      return;
    }

    Skill<?> skill = Adapt.instance.getAdaptServer().getSkillRegistry().getSkill(parts[1]);
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

}
