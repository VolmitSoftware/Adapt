package com.volmit.adapt.service;

import art.arcane.amulet.io.FolderWatcher;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.skill.SkillRegistry;
import com.volmit.adapt.api.tick.TickedObject;
import com.volmit.adapt.content.gui.ConfigGui;
import com.volmit.adapt.content.gui.SkillsGui;
import com.volmit.adapt.util.AdaptService;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.ConfigRewriteReporter;
import com.volmit.adapt.util.CustomModel;
import com.volmit.adapt.util.IO;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.Json;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.Window;
import com.volmit.adapt.util.config.ConfigFileSupport;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.volmit.adapt.util.decree.context.AdaptationListingHandler.initializeAdaptationListings;

public class HotloadSVC implements AdaptService {
    private static final long WATCHER_POLL_MS = 500;
    private static final int MAX_DIFF_MESSAGES_PER_FILE = 12;
    private static final String MISSING = "<missing>";
    private static final String REMOVED = "<removed>";

    private FolderWatcher configWatcher;
    private TickedObject configTicker;
    private File adaptFolder;
    private File adaptConfigFile;
    private File adaptConfigLegacyFile;
    private File modelsFile;
    private File modelsLegacyFile;
    private File skillsFolder;
    private File adaptationsFolder;
    private final Map<String, String> knownSignatures = new HashMap<>();
    private final Map<String, String> knownContents = new HashMap<>();

    @Override
    public void onEnable() {
        adaptFolder = Adapt.instance.getDataFolder("adapt");
        adaptConfigFile = Adapt.instance.getDataFile("adapt", "adapt.toml");
        adaptConfigLegacyFile = Adapt.instance.getDataFile("adapt", "adapt.json");
        modelsFile = Adapt.instance.getDataFile("adapt", "models.toml");
        modelsLegacyFile = Adapt.instance.getDataFile("adapt", "models.json");
        skillsFolder = Adapt.instance.getDataFolder("adapt", "skills");
        adaptationsFolder = Adapt.instance.getDataFolder("adapt", "adaptations");
        configWatcher = new FolderWatcher(adaptFolder);
        configWatcher.checkModified();
        primeKnownSnapshots();
        Adapt.info("Config hotload watcher enabled for all /adapt/*.json and /adapt/*.toml files.");

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
        configWatcher = null;
        knownSignatures.clear();
        knownContents.clear();
    }

    private void pollConfigChanges() {
        if (configWatcher == null) {
            return;
        }

        Set<File> touched = new HashSet<>();
        if (configWatcher.checkModified()) {
            touched.addAll(configWatcher.getCreated());
            touched.addAll(configWatcher.getChanged());
            touched.addAll(configWatcher.getDeleted());
        }
        touched.addAll(scanForMissedChanges());
        if (touched.isEmpty()) {
            return;
        }

        boolean refreshedSomething = false;
        for (File file : touched) {
            if (file == null || !ConfigFileSupport.isSupportedConfigFile(file)) {
                continue;
            }

            refreshedSomething = processConfigChange(file) || refreshedSomething;
        }

        if (refreshedSomething) {
            refreshOpenAdaptGuis();
        }
    }

    private boolean processConfigChange(File file) {
        String path = file.getAbsolutePath();
        String before = knownContents.get(path);
        String nowRaw = readFileContent(file);
        String now = normalizeContent(nowRaw);

        if (Objects.equals(before, now)) {
            updateKnownSnapshot(file, now);
            return false;
        }

        boolean applied = applyConfigChange(file);
        String after = normalizeContent(readFileContent(file));
        updateKnownSnapshot(file, after);
        if (!applied) {
            return false;
        }

        if (isModelsConfigFile(file)) {
            return true;
        }

        notifyOps(file, before, after);
        return true;
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
                } else {
                    Adapt.warn("Skipped hotload for " + file.getPath() + " due to invalid config.");
                }
                return ok;
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

        for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getSkills()) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                if (!adaptation.getName().equalsIgnoreCase(adaptationName)) {
                    continue;
                }

                if (!(adaptation instanceof SimpleAdaptation<?> simpleAdaptation)) {
                    return false;
                }

                boolean ok = simpleAdaptation.reloadConfigFromDisk(false);
                if (ok) {
                    Adapt.instance.getAdaptServer().getSkillRegistry().refreshRecipes(skill);
                    initializeAdaptationListings();
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

    private void refreshGlobalRuntimeSettings() {
        Adapt.wordKey.clear();
        if (AdaptConfig.get().isAutoUpdateLanguage()) {
            Localizer.updateLanguageFile();
        }

        if (AdaptConfig.get().isCustomModels()) {
            CustomModel.reloadFromDisk(true);
        } else {
            CustomModel.clear();
        }
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

    private boolean isSkillConfigFile(File file) {
        return isDirectChild(skillsFolder, file) && ConfigFileSupport.isSupportedConfigFile(file);
    }

    private boolean isAdaptationConfigFile(File file) {
        return isDirectChild(adaptationsFolder, file) && ConfigFileSupport.isSupportedConfigFile(file);
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

    private void primeKnownSnapshots() {
        knownSignatures.clear();
        knownContents.clear();
        for (File file : listKnownConfigFiles()) {
            updateKnownSnapshot(file, normalizeContent(readFileContent(file)));
        }
    }

    private Set<File> scanForMissedChanges() {
        Set<File> changed = new HashSet<>();
        Set<String> seenPaths = new HashSet<>();
        for (File file : listKnownConfigFiles()) {
            String path = file.getAbsolutePath();
            seenPaths.add(path);
            String now = signature(file);
            String previous = knownSignatures.put(path, now);
            if (previous != null && !previous.equals(now)) {
                changed.add(file);
            }
        }

        for (String path : new HashSet<>(knownSignatures.keySet())) {
            if (seenPaths.contains(path)) {
                continue;
            }

            String previous = knownSignatures.put(path, "missing");
            if (previous != null && !"missing".equals(previous)) {
                changed.add(new File(path));
            }
        }

        return changed;
    }

    private List<File> listKnownConfigFiles() {
        List<File> files = new ArrayList<>();
        Set<String> added = new HashSet<>();

        addIfConfig(files, added, adaptConfigFile);
        addIfConfig(files, added, adaptConfigLegacyFile);
        addIfConfig(files, added, modelsFile);
        addIfConfig(files, added, modelsLegacyFile);

        if (adaptFolder == null || !adaptFolder.exists() || !adaptFolder.isDirectory()) {
            return files;
        }

        ArrayDeque<File> queue = new ArrayDeque<>();
        queue.add(adaptFolder);
        while (!queue.isEmpty()) {
            File next = queue.removeFirst();
            File[] children = next.listFiles();
            if (children == null || children.length == 0) {
                continue;
            }

            for (File child : children) {
                if (child == null) {
                    continue;
                }

                if (child.isDirectory()) {
                    queue.add(child);
                    continue;
                }

                addIfConfig(files, added, child);
            }
        }

        return files;
    }

    private void addIfConfig(List<File> out, Set<String> added, File file) {
        if (file == null || !ConfigFileSupport.isSupportedConfigFile(file)) {
            return;
        }

        String path = file.getAbsolutePath();
        if (!added.add(path)) {
            return;
        }

        out.add(file);
    }

    private String signature(File file) {
        if (file == null || !file.exists()) {
            return "missing";
        }

        return file.lastModified() + ":" + file.length();
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

    private void updateKnownSnapshot(File file, String normalizedContent) {
        if (file == null) {
            return;
        }

        String path = file.getAbsolutePath();
        knownSignatures.put(path, signature(file));
        if (normalizedContent == null) {
            knownContents.remove(path);
        } else {
            knownContents.put(path, normalizedContent);
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
        List<DiffEntry> diffs = computeDiff(before, after);
        if (diffs.isEmpty()) {
            return;
        }

        String relative = relativizeToDataFolder(file);
        List<String> messages = new ArrayList<>();
        int shown = Math.min(MAX_DIFF_MESSAGES_PER_FILE, diffs.size());
        for (int i = 0; i < shown; i++) {
            DiffEntry diff = diffs.get(i);
            messages.add(formatHotloadMessage(relative, diff.key, diff.oldValue, diff.newValue));
        }

        if (diffs.size() > shown) {
            int remaining = diffs.size() - shown;
            messages.add(formatHotloadMessage(relative, "...", "+" + remaining + " more", "truncated"));
        }

        J.s(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.isOp()) {
                    continue;
                }

                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
                messages.forEach(player::sendMessage);
            }
        });
    }

    private List<DiffEntry> computeDiff(String before, String after) {
        Map<String, String> left = flattenForDiff(before);
        Map<String, String> right = flattenForDiff(after);
        Set<String> keys = new HashSet<>(left.keySet());
        keys.addAll(right.keySet());

        List<String> ordered = new ArrayList<>(keys);
        ordered.sort(String::compareTo);

        List<DiffEntry> changes = new ArrayList<>();
        for (String key : ordered) {
            boolean inLeft = left.containsKey(key);
            boolean inRight = right.containsKey(key);
            String oldValue = inLeft ? left.get(key) : MISSING;
            String newValue = inRight ? right.get(key) : REMOVED;
            if (Objects.equals(oldValue, newValue)) {
                continue;
            }
            changes.add(new DiffEntry(key, oldValue, newValue));
        }

        return changes;
    }

    private Map<String, String> flattenForDiff(String raw) {
        JsonElement element = parseStructured(raw, null);
        if (element == null) {
            Map<String, String> fallback = new HashMap<>();
            if (raw != null && !raw.isBlank()) {
                fallback.put("$", formatValue(raw));
            }
            return fallback;
        }

        Map<String, String> out = new HashMap<>();
        flattenJson("$", element, out);
        return out;
    }

    private void flattenJson(String path, JsonElement element, Map<String, String> out) {
        if (element == null || element.isJsonNull()) {
            out.put(path, "null");
            return;
        }

        if (element.isJsonPrimitive()) {
            out.put(path, element.toString());
            return;
        }

        if (element.isJsonArray()) {
            if (element.getAsJsonArray().size() == 0) {
                out.put(path, "[]");
                return;
            }

            for (int i = 0; i < element.getAsJsonArray().size(); i++) {
                flattenJson(path + "[" + i + "]", element.getAsJsonArray().get(i), out);
            }
            return;
        }

        JsonObject object = element.getAsJsonObject();
        if (object.entrySet().isEmpty()) {
            out.put(path, "{}");
            return;
        }

        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            flattenJson(path + "." + entry.getKey(), entry.getValue(), out);
        }
    }

    private String formatHotloadMessage(String file, String key, String oldValue, String newValue) {
        return C.GRAY + "[" + C.DARK_RED + "Adapt" + C.GRAY + "]: "
                + C.GREEN + "Adapt Hotloaded: "
                + C.WHITE + "[" + file + "] "
                + C.AQUA + "[" + key + "] "
                + C.GRAY + "[" + formatValue(oldValue) + " -> " + formatValue(newValue) + "]";
    }

    private String formatValue(String value) {
        if (value == null) {
            return "null";
        }

        String compact = value.replace("\r", "\\r").replace("\n", "\\n");
        if (compact.length() > 120) {
            return compact.substring(0, 117) + "...";
        }
        return compact;
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
            Map<String, Window> open = new HashMap<>(Adapt.instance.getGuiLeftovers());
            for (Map.Entry<String, Window> entry : open.entrySet()) {
                String playerKey = entry.getKey();
                Window window = entry.getValue();
                if (window == null) {
                    continue;
                }

                UUID uuid;
                try {
                    uuid = UUID.fromString(playerKey);
                } catch (Throwable ignored) {
                    continue;
                }

                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    Adapt.instance.getGuiLeftovers().remove(playerKey);
                    continue;
                }

                reopenFromTag(player, window.getTag());
            }
        });
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

    private static class DiffEntry {
        private final String key;
        private final String oldValue;
        private final String newValue;

        private DiffEntry(String key, String oldValue, String newValue) {
            this.key = key;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }
    }
}
