/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.util.common.format;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.volmlib.util.io.IO;
import art.arcane.adapt.util.config.ConfigFileSupport;
import lombok.SneakyThrows;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Objects;

public class Localizer {
    private static final Object LANGUAGE_CACHE_LOCK = new Object();
    private static String cachedPrimaryLanguage;
    private static JsonObject cachedPrimaryLanguageRoot;
    private static String cachedFallbackLanguage;
    private static JsonObject cachedFallbackLanguageRoot;

    @SneakyThrows
    public static void updateLanguageFile() {
        if (AdaptConfig.get().isAutoUpdateLanguage()) {
            Adapt.verbose("Attempting to update Language File");
            File langFolder = new File(Adapt.instance.getDataFolder() + "/languages");
            if (!langFolder.exists()) {
                langFolder.mkdirs();
            }

            Adapt.verbose("Updating Primary Language File: " + AdaptConfig.get().getLanguage());
            syncLanguageResource(langFolder, AdaptConfig.get().getLanguage());
            Adapt.verbose("Loaded Primary Language: " + AdaptConfig.get().getLanguage());

            if (!Objects.equals(AdaptConfig.get().getLanguage(), AdaptConfig.get().getFallbackLanguageDontChangeUnlessYouKnowWhatYouAreDoing())) {
                Adapt.verbose("Updating Fallback Language File: " + AdaptConfig.get().getFallbackLanguageDontChangeUnlessYouKnowWhatYouAreDoing());
                syncLanguageResource(langFolder, AdaptConfig.get().getFallbackLanguageDontChangeUnlessYouKnowWhatYouAreDoing());
                Adapt.verbose("Loaded Fallback: " + AdaptConfig.get().getFallbackLanguageDontChangeUnlessYouKnowWhatYouAreDoing());
            }

            migrateExistingLanguageFilesToToml();
        } else {
            Adapt.error("Auto Update Language is disabled, Expect Errors.");
            Adapt.error("Do not disable this unless you know what you are doing, and dont expect support.");
            migrateExistingLanguageFilesToToml();
        }

        invalidateLanguageCache();
    }


    @SneakyThrows
    public static String dLocalize(String s1, String s2, String s3) {
        return dLocalize(s1 + "." + s2 + "." + s3);
    }

    @SneakyThrows
    public static String dLocalize(String key, Object... params) {
        String cacheKey = key;
        if (!Adapt.wordKey.containsKey(cacheKey)) {
            File langFolder = new File(Adapt.instance.getDataFolder(), "languages");
            String resolved = resolveLocalizedFromRoot(getPrimaryLanguageRoot(langFolder), key);

            if (resolved == null) {
                updateLanguageFile();
                resolved = resolveLocalizedFromRoot(getPrimaryLanguageRoot(langFolder), key);
            }

            if (resolved == null) {
                Adapt.verbose("Your Language File is missing the following key: " + key);
                Adapt.verbose("Loading English Language File FallBack");

                resolved = resolveLocalizedFromRoot(getFallbackLanguageRoot(langFolder), key);
            }

            if (resolved == null) {
                Adapt.wordKey.put(cacheKey, key);
                Adapt.error("Your Fallback Language File is missing the following key: " + key);
                Adapt.verbose("New Assignement: " + key);
                Adapt.error("Please report this to the developer!");
            } else {
                Adapt.wordKey.put(cacheKey, resolved);
                Adapt.verbose("Loaded Localization: " + resolved + " for key: " + key);
            }
        }
        var s = applyParameters(Adapt.wordKey.get(cacheKey), params);
        if (AdaptConfig.get().isAutomaticGradients()) {
            s = C.translateAlternateColorCodes('&', s);
            s = C.aura(s, -20, 7, 8, 0.36);
        }

        return LegacyComponentSerializer.legacySection()
                .serialize(MiniMessage.miniMessage().deserialize(s));
    }

    private static void syncLanguageResource(File langFolder, String languageCode) throws Exception {
        if (languageCode == null || languageCode.isBlank()) {
            return;
        }

        String tomlResourcePath = languageCode + ".toml";
        String jsonResourcePath = languageCode + ".json";

        String resourcePath = tomlResourcePath;
        InputStream in = Adapt.instance.getResource(tomlResourcePath);
        if (in == null) {
            resourcePath = jsonResourcePath;
            in = Adapt.instance.getResource(jsonResourcePath);
        }

        if (in == null) {
            Adapt.warn("Missing bundled language resource: " + tomlResourcePath + " (and fallback " + jsonResourcePath + ")");
            return;
        }

        try (InputStream stream = in) {
            String raw = IO.readAll(stream);
            JsonElement parsed = ConfigFileSupport.parseToJsonElement(raw, new File(resourcePath));
            if (parsed == null) {
                Adapt.warn("Failed to parse bundled language resource: " + resourcePath);
                return;
            }

            File tomlTarget = new File(langFolder, languageCode + ".toml");
            Files.deleteIfExists(tomlTarget.toPath());
            Files.writeString(tomlTarget.toPath(), ConfigFileSupport.serializeJsonElementToToml(parsed));

            File legacyJsonTarget = new File(langFolder, jsonResourcePath);
            Files.deleteIfExists(legacyJsonTarget.toPath());
        }
    }

    private static File resolveLanguageFile(File languageFolder, String languageCode) {
        File toml = new File(languageFolder, languageCode + ".toml");
        if (toml.exists()) {
            return toml;
        }

        return new File(languageFolder, languageCode + ".json");
    }

    private static JsonObject loadLanguageRoot(File file) {
        try {
            if (file == null || !file.exists() || !file.isFile()) {
                return null;
            }

            String raw = Files.readString(file.toPath());
            JsonElement root = ConfigFileSupport.parseToJsonElement(raw, file);
            if (root == null || !root.isJsonObject()) {
                return null;
            }

            return root.getAsJsonObject();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static JsonObject getPrimaryLanguageRoot(File languageFolder) {
        synchronized (LANGUAGE_CACHE_LOCK) {
            String language = AdaptConfig.get().getLanguage();
            if (Objects.equals(language, cachedPrimaryLanguage) && cachedPrimaryLanguageRoot != null) {
                return cachedPrimaryLanguageRoot;
            }

            cachedPrimaryLanguage = language;
            cachedPrimaryLanguageRoot = loadLanguageRoot(resolveLanguageFile(languageFolder, language));
            return cachedPrimaryLanguageRoot;
        }
    }

    private static JsonObject getFallbackLanguageRoot(File languageFolder) {
        synchronized (LANGUAGE_CACHE_LOCK) {
            String fallbackLanguage = AdaptConfig.get().getFallbackLanguageDontChangeUnlessYouKnowWhatYouAreDoing();
            if (Objects.equals(fallbackLanguage, cachedFallbackLanguage) && cachedFallbackLanguageRoot != null) {
                return cachedFallbackLanguageRoot;
            }

            cachedFallbackLanguage = fallbackLanguage;
            cachedFallbackLanguageRoot = loadLanguageRoot(resolveLanguageFile(languageFolder, fallbackLanguage));
            return cachedFallbackLanguageRoot;
        }
    }

    private static String resolveLocalizedFromRoot(JsonObject root, String key) {
        if (root == null) {
            return null;
        }
        return resolveLocalizedElementValue(resolveLocalizedElement(root, key));
    }

    private static void invalidateLanguageCache() {
        synchronized (LANGUAGE_CACHE_LOCK) {
            cachedPrimaryLanguage = null;
            cachedPrimaryLanguageRoot = null;
            cachedFallbackLanguage = null;
            cachedFallbackLanguageRoot = null;
        }
        Adapt.wordKey.clear();
    }

    private static JsonElement resolveLocalizedElement(JsonObject root, String key) {
        JsonObject current = root;
        JsonElement element = null;

        for (String path : key.split("\\.")) {
            if (current == null || !current.has(path)) {
                return null;
            }

            element = current.get(path);
            if (element == null || element.isJsonNull()) {
                return null;
            }

            if (element.isJsonObject()) {
                current = element.getAsJsonObject();
            } else {
                current = null;
            }
        }

        return element;
    }

    private static String resolveLocalizedElementValue(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }

        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }

        if (element.isJsonArray()) {
            StringBuilder result = new StringBuilder();
            for (JsonElement value : element.getAsJsonArray()) {
                if (!value.isJsonPrimitive()) {
                    continue;
                }

                if (result.length() > 0) {
                    result.append('\n');
                }

                result.append(value.getAsString());
            }

            return result.toString();
        }

        return null;
    }

    private static String applyParameters(String value, Object... params) {
        if (value == null || params == null || params.length == 0) {
            return value;
        }

        String result = value;
        for (int i = 0; i < params.length; i++) {
            result = result.replace("{" + i + "}", String.valueOf(params[i]));
        }

        return result;
    }

    private static void migrateExistingLanguageFilesToToml() {
        try {
            File languageFolder = new File(Adapt.instance.getDataFolder(), "languages");
            if (!languageFolder.exists() || !languageFolder.isDirectory()) {
                return;
            }

            File[] files = languageFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
            if (files == null || files.length == 0) {
                return;
            }

            for (File jsonFile : files) {
                if (jsonFile == null || !jsonFile.exists() || !jsonFile.isFile()) {
                    continue;
                }

                File tomlFile = ConfigFileSupport.toTomlFile(jsonFile);
                if (tomlFile.exists() && tomlFile.isFile()) {
                    Files.deleteIfExists(jsonFile.toPath());
                    continue;
                }

                String raw = Files.readString(jsonFile.toPath());
                JsonElement parsed = ConfigFileSupport.parseToJsonElement(raw, jsonFile);
                if (parsed == null) {
                    continue;
                }

                Files.writeString(tomlFile.toPath(), ConfigFileSupport.serializeJsonElementToToml(parsed));
                Adapt.info("Migrated legacy language file [" + jsonFile.getName() + "] -> [" + tomlFile.getName() + "].");
                Files.deleteIfExists(jsonFile.toPath());
            }
            invalidateLanguageCache();
        } catch (Throwable e) {
            Adapt.warn("Failed to migrate legacy language json files: " + e.getMessage());
        }
    }
}
