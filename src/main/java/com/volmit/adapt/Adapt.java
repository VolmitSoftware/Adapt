package com.volmit.adapt;

import art.arcane.amulet.io.FolderWatcher;
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
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

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

    private static Map<File, Map<String, String>> words = new HashMap<>();

    public static String dLocalize(String linkKey) {
        File langFile = new File(instance.getDataFolder() + "/locales", AdaptConfig.get().getLanguage() + ".yml");
        return words.get(langFile).get(linkKey);
    }

    private static void loadLanguageLocalization() {
        File langFolder = new File(Adapt.instance.getDataFolder() + "/locales");
        if (!langFolder.exists()) {
            langFolder.mkdir();
        }
        File enFile = new File(langFolder, "en_US.yml");
        if (!enFile.exists()) {
            try {
                InputStream in = Adapt.instance.getResource("en_US.yml");
                Files.copy(in, enFile.toPath());
            } catch (IOException ignored) {
                error("Failed to load Lang file");
            }
        }
        for (File file : langFolder.listFiles()) {
            info("Loading locale " + file.getName());
            Map<String, String> localeMessages = new HashMap<>();
            FileConfiguration lang = YamlConfiguration.loadConfiguration(file);

            for (String key : lang.getKeys(false)) {
                for (String messageName : lang.getConfigurationSection(key).getKeys(true)) {
                    String message = lang.getString(key + "." + messageName);
                    localeMessages.put(messageName, message);
                }
            }
            words.put(file, localeMessages);
        }
    }

    @Override
    public void start() {
        loadLanguageLocalization();
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

            String msg = C.GRAY + "[" + C.LIGHT_PURPLE + "Adapt" + C.GRAY + "]: " + string;
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

}
