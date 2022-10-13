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
import com.volmit.adapt.api.potion.BrewingManager;
import com.volmit.adapt.api.tick.Ticker;
import com.volmit.adapt.api.value.MaterialValue;
import com.volmit.adapt.api.world.AdaptServer;
import com.volmit.adapt.commands.CommandAdapt;
import com.volmit.adapt.nms.NMS;
import com.volmit.adapt.util.*;
import com.volmit.adapt.util.secret.SecretSplash;
import de.slikey.effectlib.EffectManager;
import lombok.Getter;
import lombok.SneakyThrows;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Objects;
import java.util.Random;

public class Adapt extends VolmitPlugin {
    public static Adapt instance;
    public static HashMap<String, String> wordKey = new HashMap<>();
    public static HashMap<String, String> wordKeyOverride = new HashMap<>();
    public static BukkitAudiences audiences;
    public final EffectManager adaptEffectManager = new EffectManager(this);
    @Command
    private final CommandAdapt commandAdapt = new CommandAdapt();
    @Getter
    private Ticker ticker;
    @Getter
    private AdaptServer adaptServer;
    private FolderWatcher configWatcher;

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
        info("Language: " + AdaptConfig.get().getLanguage() + " - Language Fallback: " + AdaptConfig.get().getFallbackLanguageDontChangeUnlessYouKnowWhatYouAreDoing());
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

    @SneakyThrows
    private static void updateLanguageFile() {
        verbose("Attempting to update Language File");
        File langFolder = new File(Adapt.instance.getDataFolder() + "/languages");
        if (!langFolder.exists()) {
            langFolder.mkdir();
        }

        File langFile = new File(langFolder, AdaptConfig.get().getLanguage() + ".json");
        verbose("Updating Primary Language File: " + AdaptConfig.get().getLanguage());
        InputStream in = Adapt.instance.getResource(AdaptConfig.get().getLanguage() + ".json");
        Files.deleteIfExists(langFile.toPath());
        Files.copy(in, langFile.toPath());
        verbose("Loaded Primary Language: " + AdaptConfig.get().getLanguage());

        if (!Objects.equals(AdaptConfig.get().getLanguage(), AdaptConfig.get().getFallbackLanguageDontChangeUnlessYouKnowWhatYouAreDoing())) {
            verbose("Updating Fallback Language File: " + AdaptConfig.get().getFallbackLanguageDontChangeUnlessYouKnowWhatYouAreDoing());
            File langFileFallback = new File(langFolder, AdaptConfig.get().getFallbackLanguageDontChangeUnlessYouKnowWhatYouAreDoing() + ".json");
            InputStream inFB = Adapt.instance.getResource(AdaptConfig.get().getFallbackLanguageDontChangeUnlessYouKnowWhatYouAreDoing() + ".json");
            Files.deleteIfExists(langFileFallback.toPath());
            Files.copy(inFB, langFileFallback.toPath());
            verbose("Loaded Fallback: " + AdaptConfig.get().getFallbackLanguageDontChangeUnlessYouKnowWhatYouAreDoing());
        }
    }


    @SneakyThrows
    public static String dLocalize(String s1, String s2, String s3) {
        if (!wordKey.containsKey(s1 + s2 + s3)) { // Not in cache or Not in file

            JsonObject jsonObj;
            File langFile = new File(instance.getDataFolder() + "/languages", AdaptConfig.get().getLanguage() + ".json");
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

                    verbose("Your Language File is missing the following key: " + s1 + "." + s2 + "." + s3);
                    verbose("Loading English Language File FallBack");

                    JsonObject jsonObjFallback;
                    File langFileFallback = new File(instance.getDataFolder() + "/languages", AdaptConfig.get().getFallbackLanguageDontChangeUnlessYouKnowWhatYouAreDoing() + ".json");
                    String jsonFromFileFallback = Files.readString(langFileFallback.toPath());
                    JsonElement jsonElementFallback = JsonParser.parseString(jsonFromFileFallback);
                    jsonObjFallback = jsonElementFallback.getAsJsonObject();

                    if (jsonObjFallback.get(s1) == null
                            || jsonObjFallback.get(s1).getAsJsonObject().get(s2) == null
                            || jsonObjFallback.get(s1).getAsJsonObject().get(s2).getAsJsonObject().get(s3) == null
                            || jsonObjFallback.get(s1).getAsJsonObject().get(s2).getAsJsonObject().get(s3).getAsString() == null) {
                        String f = SecretSplash.randomString7();
                        wordKey.put(s1 + s2 + s3, f);
                        verbose("Your Fallback Language File is missing the following key: " + s1 + "." + s2 + "." + s3);
                        verbose("New Assignement: " + f);
                        verbose("Please report this to the developer!");
                    } else {
                        wordKey.put(s1 + s2 + s3, jsonObjFallback.get(s1).getAsJsonObject().get(s2).getAsJsonObject().get(s3).getAsString());
                        verbose("Loaded Fallback: " + jsonObjFallback.get(s1).getAsJsonObject().get(s2).getAsJsonObject().get(s3).getAsString() + " for key: " + s1 + "." + s2 + "." + s3);
                    }
                }
            } else {
                wordKey.put(s1 + s2 + s3, jsonObj.get(s1).getAsJsonObject().get(s2).getAsJsonObject().get(s3).getAsString());
            }
        }
        return wordKey.get(s1 + s2 + s3);
    }

    public static int getJavaVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            int dot = version.indexOf(".");
            if (dot != -1) {
                version = version.substring(0, dot);
            }
        }
        return Integer.parseInt(version);
    }

    public File getJarFile() {
        return getFile();
    }

    @Override
    public void start() {
        NMS.init();
        updateLanguageFile();
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PapiExpansion().register();
        }
        printInformation();
        ticker = new Ticker();
        sqlManager = new SQLManager();
        if (AdaptConfig.get().isUseSql()) {
            sqlManager.establishConnection();
        }
        adaptServer = new AdaptServer();
        registerListener(new BrewingManager());
        setupMetrics();
        startupPrint(); // Splash screen
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

    private void startupPrint() {
        if (!AdaptConfig.get().isSplashScreen()) {
            return;
        }
        Random r = new Random();
        int game = r.nextInt(100);

        if (game < 90) { // 90%
            Adapt.info(
                    "\n" +
                            C.GRAY + " █████" + C.DARK_RED + "╗ " + C.GRAY + "██████" + C.DARK_RED + "╗  " + C.GRAY + "█████" + C.DARK_RED + "╗ " + C.GRAY + "██████" + C.DARK_RED + "╗ " + C.GRAY + "████████" + C.DARK_RED + "╗\n" +
                            C.GRAY + "██" + C.DARK_RED + "╔══" + C.GRAY + "██" + C.DARK_RED + "╗" + C.GRAY + "██" + C.DARK_RED + "╔══" + C.GRAY + "██" + C.DARK_RED + "╗" + C.GRAY + "██" + C.DARK_RED + "╔══" + C.GRAY + "██" + C.DARK_RED + "╗" + C.GRAY + "██" + C.DARK_RED + "╔══" + C.GRAY + "██" + C.DARK_RED + "╗╚══" + C.GRAY + "██" + C.DARK_RED + "╔══╝" + C.WHITE + "         Version: " + C.DARK_RED + instance.getDescription().getVersion() + "     \n" +
                            C.GRAY + "███████" + C.DARK_RED + "║" + C.GRAY + "██" + C.DARK_RED + "║  " + C.GRAY + "██" + C.DARK_RED + "║" + C.GRAY + "███████" + C.DARK_RED + "║" + C.GRAY + "██████" + C.DARK_RED + "╔╝   " + C.GRAY + "██" + C.DARK_RED + "║" + C.WHITE + "            By: " + C.RED + "A" + C.GOLD + "r" + C.YELLOW + "c" + C.GREEN + "a" + C.DARK_GRAY + "n" + C.AQUA + "e " + C.AQUA + "A" + C.BLUE + "r" + C.DARK_BLUE + "t" + C.DARK_PURPLE + "s" + C.WHITE + " (Volmit Software)\n" +
                            C.GRAY + "██" + C.DARK_RED + "╔══" + C.GRAY + "██" + C.DARK_RED + "║" + C.GRAY + "██" + C.DARK_RED + "║  " + C.GRAY + "██" + C.DARK_RED + "║" + C.GRAY + "██" + C.DARK_RED + "╔══" + C.GRAY + "██" + C.DARK_RED + "║" + C.GRAY + "██" + C.DARK_RED + "╔═══╝    " + C.GRAY + "██" + C.DARK_RED + "║" + C.WHITE + "            Java Version: " + C.DARK_RED + getJavaVersion() + "     \n" +
                            C.GRAY + "██" + C.DARK_RED + "║  " + C.GRAY + "██" + C.DARK_RED + "║" + C.GRAY + "██████" + C.DARK_RED + "╔╝" + C.GRAY + "██" + C.DARK_RED + "║  " + C.GRAY + "██" + C.DARK_RED + "║" + C.GRAY + "██" + C.DARK_RED + "║        " + C.GRAY + "██" + C.DARK_RED + "║   \n" +
                            C.DARK_RED + "╚═╝  ╚═╝╚═════╝ ╚═╝  ╚═╝╚═╝        ╚═╝   \n"
            );
        } else {
            info(SecretSplash.getSecretSplash().getRandom());
        }


    }

}
