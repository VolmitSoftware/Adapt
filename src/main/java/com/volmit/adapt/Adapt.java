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
import com.volmit.adapt.api.data.WorldData;
import com.volmit.adapt.api.potion.BrewingManager;
import com.volmit.adapt.api.protection.DefaultProtectors;
import com.volmit.adapt.api.protection.WorldGuardProtector;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Random;

public class Adapt extends VolmitPlugin {
    public static Adapt instance;
    public static HashMap<String, String> wordKey = new HashMap<>();
    public static HashMap<String, String> wordKeyOverride = new HashMap<>();
    public static BukkitAudiences audiences;
    public final EffectManager adaptEffectManager = new EffectManager(this);
    @Command
    private final CommandAdapt commandAdapt = new CommandAdapt();
    public boolean usingMagicCosmetics = Bukkit.getServer().getPluginManager().getPlugin("MagicCosmetics") != null;
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

    @Override
    public void onLoad() {
        if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            DefaultProtectors.registerProtector(new WorldGuardProtector());
        }
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

    public static void printInformation() {
        debug("XP Curve: " + AdaptConfig.get().getXpCurve());
        debug("XP/Level base: " + AdaptConfig.get().getPlayerXpPerSkillLevelUpBase());
        debug("XP/Level multiplier: " + AdaptConfig.get().getPlayerXpPerSkillLevelUpLevelMultiplier());
        info("Language: " + AdaptConfig.get().getLanguage() + " - Language Fallback: " + AdaptConfig.get().getFallbackLanguageDontChangeUnlessYouKnowWhatYouAreDoing());
    }

    @SneakyThrows
    public static void autoUpdateCheck() {
        try {
            URL url = new URL("https://raw.githubusercontent.com/VolmitSoftware/Adapt/main/build.gradle");
            BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
            String inputLine;
            info("Checking for updates...");
            while ((inputLine = in.readLine()) != null) {
                if (inputLine.contains("version '")) {
                    String version = inputLine.remove("version '").remove("'").remove("// Needs to be version specific").remove(" ");
                    if (instance.getDescription().getVersion().contains("development")) {
                        info("Development build detected. Skipping update check.");
                        return;
                    } else if (!version.equals(instance.getDescription().getVersion())) {
                        info("Please update your Adapt plugin to the latest version! (Current: " + instance.getDescription().getVersion() + " Latest: " + version + ")");
                    } else {
                        info("You are running the latest version of Adapt!");
                    }
                    break;
                }
            }
            in.close();
        } catch (Exception e) {
            error("Failed to check for updates.");
        }
    }

    public static void actionbar(Player p, String msg) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
    }

    public static void debug(String string) {
        if (AdaptConfig.get().isDebug()) {
            msg(C.DARK_PURPLE + string);
        }
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

    public static void success(String string) {
        msg(C.GREEN + string);
    }

    public static void info(String string) {
        msg(C.WHITE + string);
    }

    public static void messagePlayer(Player p, String string) {
        String msg = C.GRAY + "[" + C.DARK_RED + "Adapt" + C.GRAY + "]: " + string;
        p.sendMessage(msg);
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

    @Override
    public void start() {
        NMS.init();
        Localizer.updateLanguageFile();
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
        if (AdaptConfig.get().isAutoUpdateCheck()) {
            autoUpdateCheck();
        }
    }

    @Override
    public void stop() {
        sqlManager.closeConnection();
        adaptServer.unregister();
        MaterialValue.save();
        WorldData.stop();
    }

    private void startupPrint() {
        if (!AdaptConfig.get().isSplashScreen()) {
            return;
        }
        Random r = new Random();
        int game = r.nextInt(100);
        if (game < 90) {
            Adapt.info("\n" + C.GRAY + " █████" + C.DARK_RED + "╗ " + C.GRAY + "██████" + C.DARK_RED + "╗  " + C.GRAY + "█████" + C.DARK_RED + "╗ " + C.GRAY + "██████" + C.DARK_RED + "╗ " + C.GRAY + "████████" + C.DARK_RED + "╗\n" +
                    C.GRAY + "██" + C.DARK_RED + "╔══" + C.GRAY + "██" + C.DARK_RED + "╗" + C.GRAY + "██" + C.DARK_RED + "╔══" + C.GRAY + "██" + C.DARK_RED + "╗" + C.GRAY + "██" + C.DARK_RED + "╔══" + C.GRAY + "██" + C.DARK_RED + "╗" + C.GRAY + "██" + C.DARK_RED + "╔══" + C.GRAY + "██" + C.DARK_RED + "╗╚══" + C.GRAY + "██" + C.DARK_RED + "╔══╝" + C.WHITE + "         Version: " + C.DARK_RED + instance.getDescription().getVersion() + "     \n" +
                    C.GRAY + "███████" + C.DARK_RED + "║" + C.GRAY + "██" + C.DARK_RED + "║  " + C.GRAY + "██" + C.DARK_RED + "║" + C.GRAY + "███████" + C.DARK_RED + "║" + C.GRAY + "██████" + C.DARK_RED + "╔╝   " + C.GRAY + "██" + C.DARK_RED + "║" + C.WHITE + "            By: " + C.RED + "A" + C.GOLD + "r" + C.YELLOW + "c" + C.GREEN + "a" + C.DARK_GRAY + "n" + C.AQUA + "e " + C.AQUA + "A" + C.BLUE + "r" + C.DARK_BLUE + "t" + C.DARK_PURPLE + "s" + C.WHITE + " (Volmit Software)\n" +
                    C.GRAY + "██" + C.DARK_RED + "╔══" + C.GRAY + "██" + C.DARK_RED + "║" + C.GRAY + "██" + C.DARK_RED + "║  " + C.GRAY + "██" + C.DARK_RED + "║" + C.GRAY + "██" + C.DARK_RED + "╔══" + C.GRAY + "██" + C.DARK_RED + "║" + C.GRAY + "██" + C.DARK_RED + "╔═══╝    " + C.GRAY + "██" + C.DARK_RED + "║" + C.WHITE + "            Java Version: " + C.DARK_RED + getJavaVersion() + "     \n" +
                    C.GRAY + "██" + C.DARK_RED + "║  " + C.GRAY + "██" + C.DARK_RED + "║" + C.GRAY + "██████" + C.DARK_RED + "╔╝" + C.GRAY + "██" + C.DARK_RED + "║  " + C.GRAY + "██" + C.DARK_RED + "║" + C.GRAY + "██" + C.DARK_RED + "║        " + C.GRAY + "██" + C.DARK_RED + "║   \n" +
                    C.DARK_RED + "╚═╝  ╚═╝╚═════╝ ╚═╝  ╚═╝╚═╝        ╚═╝   \n");
        } else {
            info(SecretSplash.getSecretSplash().getRandom());
        }
    }

    public File getJarFile() {
        return getFile();
    }

    @Override
    public String getTag(String subTag) {
        return C.BOLD + "" + C.DARK_GRAY + "[" + C.BOLD + "" + C.DARK_RED + "Adapt" + C.BOLD + C.DARK_GRAY + "]" + C.RESET + "" + C.GRAY + ": ";
    }

    private void setupMetrics() {
        if (AdaptConfig.get().isMetrics()) {
            new Metrics(this, 13412);
        }
    }


}
