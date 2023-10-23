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
import com.volmit.adapt.api.protection.ProtectorRegistry;
import com.volmit.adapt.api.tick.Ticker;
import com.volmit.adapt.api.value.MaterialValue;
import com.volmit.adapt.api.world.AdaptServer;
import com.volmit.adapt.command.CommandAdapt;
import com.volmit.adapt.content.gui.SkillsGui;
import com.volmit.adapt.content.protector.*;
import com.volmit.adapt.nms.GlowingEntities;
import com.volmit.adapt.nms.NMS;
import com.volmit.adapt.util.*;
import com.volmit.adapt.util.command.*;
import com.volmit.adapt.util.command.suggest.*;
import com.volmit.adapt.util.secret.SecretSplash;
import de.slikey.effectlib.EffectManager;
import io.github.mqzn.commands.SpigotCommandManager;
import io.github.mqzn.commands.annotations.AnnotationParser;
import io.github.mqzn.commands.base.manager.CommandExecutionCoordinator;
import lombok.Getter;
import lombok.SneakyThrows;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class Adapt extends VolmitPlugin {
    public static Adapt instance;
    public static HashMap<String, String> wordKey = new HashMap<>();
    public final EffectManager adaptEffectManager = new EffectManager(this);
    public static BukkitAudiences audiences;

    private SpigotCommandManager commandManager;
    private AnnotationParser<CommandSender> parser;

    @Getter
    private GlowingEntities glowingEntities;
    @Getter
    private Ticker ticker;
    @Getter
    private AdaptServer adaptServer;
    @Getter
    private FolderWatcher configWatcher;
    @Getter
    private SQLManager sqlManager;
    @Getter
    private ProtectorRegistry protectorRegistry;
    @Getter
    private Map<String, Window> guiLeftovers = new HashMap<>();



    

    public Adapt() {
        super();
        instance = this;
    }

    @Override
    public void start() {
        audiences = BukkitAudiences.create(this);
        commandManager = new SpigotCommandManager(this, CommandExecutionCoordinator.Type.SYNC);
        parser = new AnnotationParser<>(commandManager);
        commandManager.suggestionProviderRegistry().register(new AdaptSkillListingProvider());
        commandManager.suggestionProviderRegistry().register(new AdaptSkillProvider());

        commandManager.suggestionProviderRegistry().register(new AdaptAdaptationListingProvider());
        commandManager.suggestionProviderRegistry().register(new AdaptAdaptationProvider());

        commandManager.suggestionProviderRegistry().register(new SoundSuggestionProvider());
        commandManager.suggestionProviderRegistry().register(new ParticleSuggestionProvider());
        commandManager.suggestionProviderRegistry().register(new BooleanSuggestionProvider());

        NMS.init();
        Localizer.updateLanguageFile();
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PapiExpansion().register();
        }
        printInformation();
        sqlManager = new SQLManager();
        if (AdaptConfig.get().isUseSql()) {
            sqlManager.establishConnection();
        }
        startSim();
        registerListener(new BrewingManager());
        setupMetrics();
        startupPrint(); // Splash screen
        if (AdaptConfig.get().isAutoUpdateCheck()) {
            autoUpdateCheck();
        }
        protectorRegistry = new ProtectorRegistry();
        if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            protectorRegistry.registerProtector(new WorldGuardProtector());
        }
        if (getServer().getPluginManager().getPlugin("Factions") != null) {
            protectorRegistry.registerProtector(new FactionsClaimProtector());
        }
        if (getServer().getPluginManager().getPlugin("ChestProtect") != null) {
            protectorRegistry.registerProtector(new ChestProtectProtector());
        }
        if (getServer().getPluginManager().getPlugin("Residence") != null) {
            protectorRegistry.registerProtector(new ResidenceProtector());
        }
        if (getServer().getPluginManager().getPlugin("GriefDefender") != null) {
            protectorRegistry.registerProtector(new GriefDefenderProtector());
        }
        if (getServer().getPluginManager().getPlugin("GriefPrevention") != null) {
            protectorRegistry.registerProtector(new GriefPreventionProtector());
        }
        if (getServer().getPluginManager().getPlugin("LockettePro") != null) {
            protectorRegistry.registerProtector(new LocketteProProtector());
        }
        glowingEntities = new GlowingEntities(this);
        parser.parse(new CommandAdapt());
    }


    public void startSim() {
        ticker = new Ticker();
        adaptServer = new AdaptServer();
    }

    public void stopSim() {
        ticker.clear();
        adaptServer.unregister();
        MaterialValue.save();
        WorldData.stop();
    }


    @Override
    public void stop() {
        sqlManager.closeConnection();
        stopSim();
        protectorRegistry.unregisterAll();
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
        try (BufferedReader in = new BufferedReader(new InputStreamReader(new URL("https://raw.githubusercontent.com/VolmitSoftware/Adapt/main/build.gradle").openStream()))) {
            info("Checking for updates...");
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                if (inputLine.contains("version '")) {
                    String version = inputLine.replace("version '", "").replace("'", "").replace("// Needs to be version specific", "").replace(" ", "");
                    if (instance.getDescription().getVersion().contains("development")) {
                        info("Development build detected. Skipping update check.");
                        return;
                    } else if (!version.equals(instance.getDescription().getVersion())) {
                        info(MessageFormat.format("Please update your Adapt plugin to the latest version! (Current: {0} Latest: {1})", instance.getDescription().getVersion(), version));
                    } else {
                        info("You are running the latest version of Adapt!");
                    }
                    break;
                }
            }
        } catch (Throwable e) {
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

    public static void hotloaded() {
        J.s(() -> {
            instance.guiLeftovers.values().forEach(window -> {
                HandlerList.unregisterAll((Listener) window);
                window.close();
            });
            instance.stop();
            instance.start();

            instance.getGuiLeftovers().forEach((s, window) -> {

                if (window.getTag() != null) {
                    if (window.getTag().equals("/")) {
                        SkillsGui.open(Bukkit.getPlayer(UUID.fromString(s)));
                    } else {
                        String[] split = window.getTag().split("\\Q/\\E");
                        if (split.length == 2) {
                            if (split[0].equals("skill")) {
                                instance.getAdaptServer().getSkillRegistry().getSkill(split[1]).openGui(Bukkit.getPlayer(UUID.fromString(s)));
                            }
                        } else if (split.length == 3) {
                            if (split[0].equals("skill")) {
                                try {
                                    instance.getAdaptServer().getSkillRegistry().getSkill(split[1]).getAdaptations().where(a -> a.getId().equals(split[2])).get(0).openGui(Bukkit.getPlayer(UUID.fromString(s)));

                                } catch (Throwable e) {
                                    instance.getAdaptServer().getSkillRegistry().getSkill(split[1]).openGui(Bukkit.getPlayer(UUID.fromString(s)));
                                }
                            }
                        }
                    }

                }
            });

        }, 20);
    }
}
