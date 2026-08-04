package art.arcane.adapt.service;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.MutationManager;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.content.gui.ConfigGui;
import art.arcane.adapt.content.gui.MutationGui;
import art.arcane.adapt.content.gui.SkillsGui;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RuntimeMessages;
import art.arcane.adapt.util.common.io.Json;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.plugin.AdaptService;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigFileSupport;
import art.arcane.adapt.util.project.config.ConfigRewriteReporter;
import art.arcane.volmlib.util.hotload.ConfigHotloadEngine;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.io.IO;
import com.google.gson.JsonElement;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static art.arcane.adapt.util.director.context.AdaptationListingHandler.initializeAdaptationListings;
import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

public class HotloadSVC implements AdaptService {
  private static final long WATCHER_POLL_MS = 500;
  private static final int MAX_DIFF_MESSAGES_PER_FILE = 12;
  private static final int MUTATION_RECONCILIATION_BATCH_SIZE = 32;

  private TickedObject configTicker;
  private File adaptConfigFile;
  private File adaptConfigLegacyFile;
  private File modelsFile;
  private File modelsLegacyFile;
  private File mutationsConfigFile;
  private File skillsFolder;
  private File adaptationsFolder;
  private File localeOverrideFolder;
  private final ConfigHotloadEngine hotloadEngine = new ConfigHotloadEngine(
      this::isManagedConfigFile,
      this::listKnownConfigFiles,
      this::readFileContent,
      this::normalizeContent
  );

  @Override
  public void onEnable() {
    adaptConfigFile = Adapt.instance.getDataFile("adapt", "adapt.toml");
    adaptConfigLegacyFile = Adapt.instance.getDataFile("adapt", "adapt.json");
    modelsFile = Adapt.instance.getDataFile("adapt", "models.toml");
    modelsLegacyFile = Adapt.instance.getDataFile("adapt", "models.json");
    mutationsConfigFile = Adapt.instance.getDataFile("adapt", "mutations.toml");
    skillsFolder = Adapt.instance.getDataFolder("adapt", "skills");
    adaptationsFolder = Adapt.instance.getDataFolder("adapt", "adaptations");
    localeOverrideFolder = AdaptLanguage.overrideFolder();
    hotloadEngine.configure(
        WATCHER_POLL_MS,
        List.of(adaptConfigFile, adaptConfigLegacyFile, modelsFile, modelsLegacyFile, mutationsConfigFile),
        List.of(skillsFolder, adaptationsFolder, localeOverrideFolder)
    );
    Adapt.info("Config hotload watcher enabled for Adapt configs and locale overrides.");

    configTicker = new TickedObject("config", "config-hotload-service", WATCHER_POLL_MS) {
      @Override
      public void onTick() {
        pollConfigChanges();
      }
    };
  }

  @Override
  public void onDisable() {
    if (configTicker != null) {
      configTicker.unregister();
      configTicker = null;
    }
    hotloadEngine.clear();
  }

  private void pollConfigChanges() {
    java.util.Set<java.io.File> touched = hotloadEngine.pollTouchedFiles();
    if (touched.isEmpty()) {
      return;
    }

    boolean refreshedSomething = false;
    for (File file : touched) {
      refreshedSomething = processConfigChange(file) || refreshedSomething;
    }

    if (refreshedSomething) {
      refreshOpenAdaptGuis();
    }
  }

  private boolean processConfigChange(File file) {
    return hotloadEngine.processFileChange(file, this::applyConfigChange, delta -> {
      if (isModelsConfigFile(file)) {
        return;
      }

      notifyOps(file, delta.before(), delta.after());
    });
  }

  private boolean applyConfigChange(File file) {
    try {
      if (isShadowedLegacyJson(file)) {
        if (!isModelsConfigFile(file)) {
          Adapt.verbose("Ignoring legacy json hotload because canonical toml exists: " + file.getPath());
        }
        return false;
      }

      if (isAdaptConfigFile(file)) {
        boolean ok = AdaptConfig.reload();
        if (ok) {
          refreshGlobalRuntimeSettings();
          reconcileCurrentMutationQualification();
        } else {
          Adapt.warn("Skipped hotload for " + file.getPath() + " due to invalid config.");
        }
        return ok;
      }

      if (isLocaleOverrideFile(file)) {
        boolean ok = AdaptLanguage.reload();
        if (ok) {
          Adapt.instance.getAdaptServer().getSkillRegistry().synchronizeAdvancementRuntime();
        }
        return ok;
      }

      if (isMutationsConfigFile(file)) {
        return reloadMutationsConfig(file);
      }

      if (isSkillConfigFile(file)) {
        return reloadSkillConfig(file);
      }

      if (isAdaptationConfigFile(file)) {
        return reloadAdaptationConfig(file);
      }

      if (isModelsConfigFile(file)) {
        return reloadModelsConfig(file);
      }

      return validateAndCanonicalizeConfig(file);
    } catch (Throwable e) {
      Adapt.warn("Skipped hotload for " + file.getPath() + " due to invalid config: " + e.getMessage());
      return false;
    }
  }

  private boolean reloadSkillConfig(File file) {
    String skillName = toConfigName(file.getName());
    if (skillName == null) {
      return false;
    }

    SkillRegistry registry = Adapt.instance.getAdaptServer().getSkillRegistry();
    boolean ok = registry.hotReloadSkillConfig(skillName);
    if (ok) {
      initializeAdaptationListings();
      reconcileCurrentMutationQualification();
    } else {
      Adapt.warn("Skipped hotload for " + file.getPath() + " due to invalid skill config.");
    }
    return ok;
  }

  private boolean reloadAdaptationConfig(File file) {
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
        boolean ok = registry.hotReloadAdaptationConfig(adaptationName);
        if (ok) {
          initializeAdaptationListings();
          reconcileCurrentMutationQualification();
        } else {
          Adapt.warn("Skipped hotload for " + file.getPath() + " due to invalid adaptation config.");
        }
        return ok;
      }
    }

    return validateAndCanonicalizeConfig(file);
  }

  private boolean reloadModelsConfig(File file) {
    return CustomModel.reloadFromDisk(true);
  }

  private boolean reloadMutationsConfig(File file) {
    MutationSVC mutationService = MutationSVC.get();
    if (mutationService == null || !mutationService.reload()) {
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
    AdaptLanguage.reload();

    if (AdaptConfig.get().isCustomModels()) {
      CustomModel.reloadFromDisk(true);
    } else {
      CustomModel.clear();
    }

    Adapt.instance.getAdaptServer().getSkillRegistry().synchronizeAdvancementRuntime();
  }

  private boolean validateAndCanonicalizeConfig(File file) {
    if (file == null || !file.exists() || !file.isFile()) {
      return true;
    }

    try {
      String raw = readFileContent(file);
      JsonElement parsed = parseStructured(raw, file);
      if (parsed == null) {
        return false;
      }

      if (ConfigFileSupport.isTomlFile(file)) {
        return true;
      }

      String canonical = Json.toJson(parsed, true);
      if (!normalizeContent(raw).equals(normalizeContent(canonical))) {
        ConfigRewriteReporter.reportRewrite(file, "hotload", raw, canonical);
        IO.writeAll(file, canonical);
      }
      return true;
    } catch (Throwable e) {
      return false;
    }
  }

  private boolean isAdaptConfigFile(File file) {
    return sameFile(file, adaptConfigFile) || sameFile(file, adaptConfigLegacyFile);
  }

  private boolean isModelsConfigFile(File file) {
    return sameFile(file, modelsFile) || sameFile(file, modelsLegacyFile);
  }

  private boolean isMutationsConfigFile(File file) {
    return sameFile(file, mutationsConfigFile);
  }

  private boolean isSkillConfigFile(File file) {
    return isDirectChild(skillsFolder, file) && ConfigFileSupport.isSupportedConfigFile(file);
  }

  private boolean isAdaptationConfigFile(File file) {
    return isDirectChild(adaptationsFolder, file) && ConfigFileSupport.isSupportedConfigFile(file);
  }

  private boolean isLocaleOverrideFile(File file) {
    return isDirectChild(localeOverrideFolder, file)
        && file.getName().toLowerCase(Locale.ROOT).endsWith(".toml");
  }

  private boolean isManagedConfigFile(File file) {
    return isAdaptConfigFile(file)
        || isModelsConfigFile(file)
        || isMutationsConfigFile(file)
        || isSkillConfigFile(file)
        || isAdaptationConfigFile(file)
        || isLocaleOverrideFile(file);
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

  private boolean isShadowedLegacyJson(File file) {
    if (file == null || !file.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
      return false;
    }

    if (sameFile(file, adaptConfigLegacyFile) && adaptConfigFile != null && adaptConfigFile.exists()) {
      return true;
    }
    if (sameFile(file, modelsLegacyFile) && modelsFile != null && modelsFile.exists()) {
      return true;
    }
    if ((isSkillConfigFile(file) || isAdaptationConfigFile(file)) && ConfigFileSupport.toTomlFile(file).exists()) {
      return true;
    }

    return false;
  }

  private String toConfigName(String fileName) {
    return ConfigFileSupport.configNameFromFileName(fileName);
  }

  private List<File> listKnownConfigFiles() {
    List<File> files = new ArrayList<>();
    Map<String, File> added = new HashMap<>();

    addIfManaged(files, added, adaptConfigFile);
    addIfManaged(files, added, adaptConfigLegacyFile);
    addIfManaged(files, added, modelsFile);
    addIfManaged(files, added, modelsLegacyFile);
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

    try {
      return Files.readString(file.toPath());
    } catch (Throwable e) {
      return null;
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
      for (Player player : Adapt.instance.getAdaptServer().getOnlinePlayerSnapshot()) {
        if (!player.isOp()) {
          continue;
        }

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
        messages.forEach(player::sendMessage);
      }
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
