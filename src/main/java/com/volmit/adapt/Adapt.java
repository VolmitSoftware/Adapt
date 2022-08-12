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
import lombok.SneakyThrows;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

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


    public static String dLocalize(String s1, String s2, String s3) {
        JsonObject jsonObj = null;
        try {
            File langFile = new File(instance.getDataFolder() + "/languages", AdaptConfig.get().getLanguage() + ".json");
            String jsonFromFile = Files.readString(langFile.toPath());
            JsonElement jsonElement = JsonParser.parseString(jsonFromFile); // Get the file as a JsonElement
            jsonObj = jsonElement.getAsJsonObject(); //since you know it's a JsonObject
        } catch (IOException e) {
            error("Failed to load the json String");
        }
        return jsonObj.get(s1).getAsJsonObject().get(s2).getAsJsonObject().get(s3).getAsString();

    }

    private static void loadLanguageLocalization() {
        warn("Loading Language File");

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
        warn("Language Files Loaded");
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
