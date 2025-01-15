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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import lombok.SneakyThrows;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
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
            Files.copy(Objects.requireNonNull(in), langFile.toPath());
            Adapt.verbose("Loaded Primary Language: " + AdaptConfig.get().getLanguage());

            if (!Objects.equals(AdaptConfig.get().getLanguage(), AdaptConfig.get().getFallbackLanguageDontChangeUnlessYouKnowWhatYouAreDoing())) {
                Adapt.verbose("Updating Fallback Language File: " + AdaptConfig.get().getFallbackLanguageDontChangeUnlessYouKnowWhatYouAreDoing());
                File langFileFallback = new File(langFolder, AdaptConfig.get().getFallbackLanguageDontChangeUnlessYouKnowWhatYouAreDoing() + ".json");
                InputStream inFB = Adapt.instance.getResource(AdaptConfig.get().getFallbackLanguageDontChangeUnlessYouKnowWhatYouAreDoing() + ".json");
                Files.deleteIfExists(langFileFallback.toPath());
                Files.copy(Objects.requireNonNull(inFB), langFileFallback.toPath());
                Adapt.verbose("Loaded Fallback: " + AdaptConfig.get().getFallbackLanguageDontChangeUnlessYouKnowWhatYouAreDoing());
            }
        } else {
            Adapt.error("Auto Update Language is disabled, Expect Errors.");
            Adapt.error("Do not disable this unless you know what you are doing, and dont expect support.");
        }
    }


    @SneakyThrows
    public static String dLocalize(String s1, String s2, String s3) {
        if (!Adapt.wordKey.containsKey(s1 + s2 + s3)) { // Not in cache or Not in file

            JsonObject jsonObj;
            File langFile = new File(Adapt.instance.getDataFolder() + "/languages", AdaptConfig.get().getLanguage() + ".json");
            String jsonFromFile = Files.readString(langFile.toPath());
            JsonElement jsonElement = JsonParser.parseString(jsonFromFile);
            jsonObj = jsonElement.getAsJsonObject();

            if (jsonObj.get(s1) == null
                    || jsonObj.get(s1).getAsJsonObject().get(s2) == null
                    || jsonObj.get(s1).getAsJsonObject().get(s2).getAsJsonObject().get(s3) == null
                    || jsonObj.get(s1).getAsJsonObject().get(s2).getAsJsonObject().get(s3).getAsString() == null) {

                updateLanguageFile();
                if (jsonObj.get(s1) == null
                        || jsonObj.get(s1).getAsJsonObject().get(s2) == null
                        || jsonObj.get(s1).getAsJsonObject().get(s2).getAsJsonObject().get(s3) == null
                        || jsonObj.get(s1).getAsJsonObject().get(s2).getAsJsonObject().get(s3).getAsString() == null) {

                    String key = s1 + "." + s2 + "." + s3;
                    Adapt.verbose("Your Language File is missing the following key: " + key);
                    Adapt.verbose("Loading English Language File FallBack");

                    JsonObject jsonObjFallback;
                    File langFileFallback = new File(Adapt.instance.getDataFolder() + "/languages", AdaptConfig.get().getFallbackLanguageDontChangeUnlessYouKnowWhatYouAreDoing() + ".json");
                    String jsonFromFileFallback = Files.readString(langFileFallback.toPath());
                    JsonElement jsonElementFallback = JsonParser.parseString(jsonFromFileFallback);
                    jsonObjFallback = jsonElementFallback.getAsJsonObject();

                    if (jsonObjFallback.get(s1) == null
                            || jsonObjFallback.get(s1).getAsJsonObject().get(s2) == null
                            || jsonObjFallback.get(s1).getAsJsonObject().get(s2).getAsJsonObject().get(s3) == null
                            || jsonObjFallback.get(s1).getAsJsonObject().get(s2).getAsJsonObject().get(s3).getAsString() == null) {
                        Adapt.wordKey.put(s1 + s2 + s3, key);
                        Adapt.error("Your Fallback Language File is missing the following key: " + key);
                        Adapt.verbose("New Assignement: " + key);
                        Adapt.error("Please report this to the developer!");
                    } else {
                        Adapt.wordKey.put(s1 + s2 + s3, jsonObjFallback.get(s1).getAsJsonObject().get(s2).getAsJsonObject().get(s3).getAsString());
                        Adapt.verbose("Loaded Fallback: " + jsonObjFallback.get(s1).getAsJsonObject().get(s2).getAsJsonObject().get(s3).getAsString() + " for key: " + s1 + "." + s2 + "." + s3);
                    }
                }
            } else {
                Adapt.wordKey.put(s1 + s2 + s3, jsonObj.get(s1).getAsJsonObject().get(s2).getAsJsonObject().get(s3).getAsString());
            }
        }
        var s = Adapt.wordKey.get(s1 + s2 + s3);
        if (AdaptConfig.get().isAutomaticGradients()) {
            s = C.translateAlternateColorCodes('&', s);
            s = C.aura(s, -20, 7, 8, 0.36);
        }

        return LegacyComponentSerializer.legacySection()
                .serialize(MiniMessage.miniMessage().deserialize(s));
    }
}
