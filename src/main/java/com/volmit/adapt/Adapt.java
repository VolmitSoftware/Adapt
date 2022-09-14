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
import com.volmit.adapt.util.*;
import de.slikey.effectlib.EffectManager;
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
    public static Adapt instance;
    public static HashMap<String, String> wordKey = new HashMap<>();
    public final EffectManager adaptEffectManager = new EffectManager(this);
    @Command
    private CommandAdapt commandAdapt = new CommandAdapt();
    @Getter
    private Ticker ticker;
    @Getter
    private AdaptServer adaptServer;
    private FolderWatcher configWatcher;
    private boolean localized = false;

    @Getter
    private SQLManager sqlManager;

    public Adapt() {
        super();
        instance = this;
    }

    public static void actionbar(Player p, String msg) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
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

    private static void updateLanguageFile() {
        info("Attempting to update Language File");
        File langFolder = new File(Adapt.instance.getDataFolder() + "/languages");
        if (!langFolder.exists()) {
            langFolder.mkdir();
        }

        File langFile = new File(langFolder, AdaptConfig.get().getLanguage() + ".json");
        if (langFile.exists()) {
            try {
                InputStream in = Adapt.instance.getResource(AdaptConfig.get().getLanguage() + ".json");
                Files.deleteIfExists(langFile.toPath());
                Files.copy(in, langFile.toPath());
                info("Language File Updated");
            } catch (IOException ignored) {
                error("Failed to load Internal Lang file");
            }
        } else {
            loadLanguageLocalization();
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

    public static String dLocalize(String s1, String s2, String s3) {
        if (!wordKey.containsKey("" + s1 + s2 + s3)) {
            JsonObject jsonObj = null;
            try {
                File langFile = new File(instance.getDataFolder() + "/languages", AdaptConfig.get().getLanguage() + ".json");
                String jsonFromFile = Files.readString(langFile.toPath());
                JsonElement jsonElement = JsonParser.parseString(jsonFromFile); // Get the file as a JsonElement
                jsonObj = jsonElement.getAsJsonObject(); //since you know it's a JsonObject
                if (jsonObj.get(s1).getAsJsonObject().get(s2).getAsJsonObject().get(s3).getAsString() == null && !instance.localized) {
                    updateLanguageFile();
                    instance.localized = true;
                }
            } catch (Exception e) {
                updateLanguageFile();
                try {
                    File langFile = new File(instance.getDataFolder() + "/languages", AdaptConfig.get().getLanguage() + ".json");
                    String jsonFromFile = Files.readString(langFile.toPath());
                    JsonElement jsonElement = JsonParser.parseString(jsonFromFile); // Get the file as a JsonElement
                    jsonObj = jsonElement.getAsJsonObject(); //since you know it's a JsonObject
                } catch (IOException e1) {
                    error("Failed to load the json String: " + s1 + s2 + s3);

                    e1.printStackTrace();
                }
            }

            wordKey.put("" + s1 + s2 + s3, jsonObj.get(s1).getAsJsonObject().get(s2).getAsJsonObject().get(s3).getAsString());
            return jsonObj.get(s1).getAsJsonObject().get(s2).getAsJsonObject().get(s3).getAsString();
        } else {
            return wordKey.get("" + s1 + s2 + s3);
        }
    }

    public File getJarFile() {
        return getFile();
    }

    @Override
    public void start() {
        loadLanguageLocalization();
        printInformation();
        NMS.init();
        ticker = new Ticker();
        adaptServer = new AdaptServer();
        sqlManager = new SQLManager();
        if(AdaptConfig.get().isUseSql()) {
            sqlManager.establishConnection();
        }
        setupMetrics();
    }

    private void setupMetrics() {
        if (AdaptConfig.get().isMetrics()) {
            new Metrics(this, 13412);
        }
    }

    @Override
    public void stop() {
        sqlManager.closeConnection();
        adaptServer.unregister();
        MaterialValue.save();
        WorldData.stop();
    }

    @Override
    public String getTag(String subTag) {
        return C.BOLD + "" + C.DARK_GRAY + "[" + C.BOLD + "" + C.LIGHT_PURPLE + "Adapt" + C.BOLD + C.DARK_GRAY + "]" + C.RESET + "" + C.GRAY + ": ";
    }


}
