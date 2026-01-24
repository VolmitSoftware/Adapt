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

package com.volmit.adapt.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import lombok.SneakyThrows;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.Objects;

public class Localizer {

    @SneakyThrows
    public static void updateLanguageFile() {
        if (AdaptConfig.get().isAutoUpdateLanguage()) {

            Adapt.verbose("Attempting to update Language File");
            File langFolder = new File(Adapt.instance.getDataFolder() + "/languages");
            if (!langFolder.exists()) {
                langFolder.mkdir();
            }

            File langFile = new File(langFolder, AdaptConfig.get().getLanguage() + ".json");
            Adapt.verbose("Updating Primary Language File: " + AdaptConfig.get().getLanguage());
            InputStream in = Adapt.instance.getResource(AdaptConfig.get().getLanguage() + ".json");
            Files.deleteIfExists(langFile.toPath());
            Files.copy(in, langFile.toPath());
            Adapt.verbose("Loaded Primary Language: " + AdaptConfig.get().getLanguage());

            if (!Objects.equals(AdaptConfig.get().getLanguage(), AdaptConfig.get().getFallbackLanguageDontChangeUnlessYouKnowWhatYouAreDoing())) {
                Adapt.verbose("Updating Fallback Language File: " + AdaptConfig.get().getFallbackLanguageDontChangeUnlessYouKnowWhatYouAreDoing());
                File langFileFallback = new File(langFolder, AdaptConfig.get().getFallbackLanguageDontChangeUnlessYouKnowWhatYouAreDoing() + ".json");
                InputStream inFB = Adapt.instance.getResource(AdaptConfig.get().getFallbackLanguageDontChangeUnlessYouKnowWhatYouAreDoing() + ".json");
                Files.deleteIfExists(langFileFallback.toPath());
                Files.copy(inFB, langFileFallback.toPath());
                Adapt.verbose("Loaded Fallback: " + AdaptConfig.get().getFallbackLanguageDontChangeUnlessYouKnowWhatYouAreDoing());
            }
        } else {
            Adapt.error("Auto Update Language is disabled, Expect Errors.");
            Adapt.error("Do not disable this unless you know what you are doing, and dont expect support.");
        }
    }

    @SneakyThrows
    public static String dLocalize(String key, Object... params) {
        if (!Adapt.wordKey.containsKey(key)) Adapt.wordKey.put(key, loadValue(key));
        String s = Adapt.wordKey.get(key);
        if (params != null && params.length > 0) s = MessageFormat.format(s, params);
        if (AdaptConfig.get().isAutomaticGradients()) {
            s = C.translateAlternateColorCodes('&', s);
            s = C.aura(s, -20, 7, 8, 0.36);
        }
        return LegacyComponentSerializer.legacySection().serialize(MiniMessage.miniMessage().deserialize(s));
    }

    private static String loadValue(String key) throws Exception {
        String primaryLang = AdaptConfig.get().getLanguage();
        String fallbackLang = AdaptConfig.get().getFallbackLanguageDontChangeUnlessYouKnowWhatYouAreDoing();

        String value = resolveValue(loadLanguage(primaryLang), key);
        if (value != null) return value;

        updateLanguageFile();
        value = resolveValue(loadLanguage(primaryLang), key);
        if (value != null) return value;

        Adapt.verbose("Your Language File is missing the following key: " + key);
        Adapt.verbose("Loading English Language File FallBack");
        value = resolveValue(loadLanguage(fallbackLang), key);
        if (value != null) {
            Adapt.verbose("Loaded Fallback for key: " + key);
            return value;
        }
        Adapt.error("Your Fallback Language File is missing the following key: " + key);
        Adapt.verbose("New Assignement: " + key);
        Adapt.error("Please report this to the developer!");
        return key;
    }

    private static JsonObject loadLanguage(String lang) throws Exception {
        File langFile = new File(Adapt.instance.getDataFolder() + "/languages", lang + ".json");
        String jsonFromFile = Files.readString(langFile.toPath());
        return Json.fromJson(jsonFromFile, JsonObject.class);
    }

    private static JsonElement resolveElement(JsonObject root, String key) {
        String[] parts = key.split("\\.");
        JsonObject current = root;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (current == null) return null;
            JsonElement element = current.get(part);
            if (element == null) return null;
            if (i == parts.length - 1) return element;
            if (!element.isJsonObject()) return null;
            current = element.getAsJsonObject();
        }
        return null;
    }

    private static String resolveValue(JsonObject root, String key) {
        JsonElement element = resolveElement(root, key);
        if (element == null) return null;
        if (element.isJsonPrimitive()) return element.getAsString();
        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            StringBuilder sb = new StringBuilder();
            arr.forEach(e -> {
                if (!e.isJsonPrimitive()) return;
                if (!sb.isEmpty()) sb.append("\n");
                sb.append(e.getAsJsonPrimitive().getAsString());
            });
            return sb.toString();
        }
        return null;
    }
}
