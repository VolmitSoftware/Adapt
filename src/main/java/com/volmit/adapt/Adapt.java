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

package com.volmit.adapt;

import art.arcane.amulet.io.FolderWatcher;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.volmit.adapt.api.data.WorldData;
import com.volmit.adapt.api.tick.Ticker;
import com.volmit.adapt.api.value.MaterialValue;
import com.volmit.adapt.api.world.AdaptServer;
import com.volmit.adapt.commands.CommandAdapt;
import com.volmit.adapt.nms.NMS;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Command;
import com.volmit.adapt.util.Metrics;
import com.volmit.adapt.util.VolmitPlugin;
import lombok.Getter;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;

public class Adapt extends VolmitPlugin {
    @Command
    private CommandAdapt commandAdapt = new CommandAdapt();
    public static Adapt instance;

    @Getter
    private Ticker ticker;

    @Getter
    private AdaptServer adaptServer;
    private FolderWatcher configWatcher;

    public Adapt() {
        super();
        instance = this;
    }

    public static void actionbar(Player p, String msg) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
    }

    public File getJarFile() {
        return getFile();
    }

    public static HashMap<String, String> wordKey = new HashMap<>();

    public static String dLocalize(String s1, String s2, String s3) {
        if (!wordKey.containsKey(""+s1+s2+s3)) {
            JsonObject jsonObj = null;
            try {
                File langFile = new File(instance.getDataFolder() + "/languages", AdaptConfig.get().getLanguage() + ".json");
                String jsonFromFile = Files.readString(langFile.toPath());
                JsonElement jsonElement = JsonParser.parseString(jsonFromFile); // Get the file as a JsonElement
                jsonObj = jsonElement.getAsJsonObject(); //since you know it's a JsonObject
            } catch (IOException e) {
                error("Failed to load the json String: " + s1 + s2 + s3);
            }
            if (jsonObj == null || jsonObj.get(s1) == null) { // Lang key does not exist in the lang Dictionary
                File langFileEN = new File(instance.getDataFolder() + "/languages", "en_US.json");
                InputStream in = Adapt.instance.getResource("en_US.json");
                if (!langFileEN.exists()) {
                    try {
                        if (in != null) {
                            Files.copy(in, langFileEN.toPath());
                            info("Created default language file: " + langFileEN.getName());
                            String jsonFromFile = Files.readString(langFileEN.toPath());
                            JsonElement jsonElement = JsonParser.parseString(jsonFromFile); // Get the file as a JsonElement
                            jsonObj = jsonElement.getAsJsonObject(); //since you know it's a JsonObject
                        } else {
                            error("Your Jar is corrupted, please reinstall the plugin");
                        }
                    } catch (IOException ignored) {
                        error("Failed to load Lang file");
                    }
                } else {
                    try {
                        String jsonFromFile = Files.readString(langFileEN.toPath());
                        JsonElement jsonElement = JsonParser.parseString(jsonFromFile); // Get the file as a JsonElement
                        jsonObj = jsonElement.getAsJsonObject(); //since you know it's a JsonObject
                    } catch (IOException e) {
                        error("Failed to load fallback english language file");
                    }
                }
                info(s1 + s2 + s3 + " Language Key is null, Using English Variant");
            }
            wordKey.put("" + s1 + s2 + s3, jsonObj.get(s1).getAsJsonObject().get(s2).getAsJsonObject().get(s3).getAsString());
            return jsonObj.get(s1).getAsJsonObject().get(s2).getAsJsonObject().get(s3).getAsString();
        } else {
            return wordKey.get(""+s1+s2+s3);
        }
    }

    private static void loadLanguageLocalization() {
        info("Loading Language File");
        File langFolder = new File(Adapt.instance.getDataFolder() + "/languages");
        if (!langFolder.exists()) {
            langFolder.mkdir();
        }

        File langFile = new File(langFolder, AdaptConfig.get().getLanguage() + ".json");
        if (!langFile.exists()) {
            try {
                InputStream in = Adapt.instance.getResource(AdaptConfig.get().getLanguage() + ".json");
                Files.copy(in, langFile.toPath());
            } catch (IOException ignored) {
                error("Failed to load Lang file");
            }
        }
        info("Language Files Loaded");
    }


    @Override
    public void start() {
        loadLanguageLocalization();
        printInformation();
        NMS.init();
        ticker = new Ticker();
        adaptServer = new AdaptServer();
        setupMetrics();
    }

    private void setupMetrics() {
        if (AdaptConfig.get().isMetrics()) {
            new Metrics(this, 13412);
        }
    }

    @Override
    public void stop() {
        adaptServer.unregister();
        MaterialValue.save();
        WorldData.stop();
    }

    @Override
    public String getTag(String subTag) {
        return C.BOLD + "" + C.DARK_GRAY + "[" + C.BOLD + "" + C.LIGHT_PURPLE + "Adapt" + C.BOLD + C.DARK_GRAY + "]" + C.RESET + "" + C.GRAY + ": ";
    }

    public static void printInformation() {
        debug("XP Curve: " + AdaptConfig.get().getXpCurve());
        debug("XP/Level base: " + AdaptConfig.get().getPlayerXpPerSkillLevelUpBase());
        debug("XP/Level multiplier: " + AdaptConfig.get().getPlayerXpPerSkillLevelUpLevelMultiplier());
        info("Language: " + AdaptConfig.get().getLanguage());
    }

    public static void warn(String string) {
        msg(C.YELLOW + string);
    }

    public static void error(String string) {
        msg(C.RED + string);
    }

    public static void verbose(String string) {
        if (AdaptConfig.get().isVerbose()) {
            msg(C.LIGHT_PURPLE + string);
        }
    }

    public static void msg(String string) {
        try {
            if (instance == null) {
                System.out.println("[Adapt]: " + string);
                return;
            }

            String msg = C.GRAY + "[" + C.DARK_RED + "Adapt" + C.GRAY + "]: " + string;
            Bukkit.getConsoleSender().sendMessage(msg);
        } catch (Throwable e) {
            System.out.println("[Adapt]: " + string);
        }
    }

    public static void success(String string) {
        msg(C.GREEN + string);
    }

    public static void info(String string) {
        msg(C.WHITE + string);
    }

    public static void debug(String string) {
        if (AdaptConfig.get().isDebug()) {
            msg(C.DARK_PURPLE + string);
        }
    }

}
