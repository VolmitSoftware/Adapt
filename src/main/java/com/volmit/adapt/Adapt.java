package com.volmit.adapt;

import com.volmit.adapt.api.tick.Ticker;
import com.volmit.adapt.api.value.MaterialValue;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.VolmitPlugin;
import com.volmit.adapt.api.world.AdaptServer;
import lombok.Getter;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;

public class Adapt extends VolmitPlugin
{
    public static Adapt instance;

    @Getter
    private Ticker ticker;
    @Getter
    private AdaptServer adaptServer;

    public Adapt()
    {
        super();
        instance = this;
    }

    public static void actionbar(Player p, String msg)
    {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
    }

    public File getJarFile()
    {
        return getFile();
    }

    @Override
    public void start() {
        ticker = new Ticker();
        adaptServer = new AdaptServer();
    }

    @Override
    public void stop() {
        adaptServer.unregister();
        MaterialValue.save();
    }

    @Override
    public String getTag(String subTag) {
        return C.BOLD + "" + C.DARK_GRAY + "[" + C.BOLD + "" + C.LIGHT_PURPLE + "Adapt" + C.BOLD + C.DARK_GRAY + "]" + C.RESET + "" + C.GRAY + ": ";
    }

    public static void warn(String string)
    {
        msg(C.YELLOW + string);
    }

    public static void error(String string)
    {
        msg(C.RED + string);
    }

    public static void verbose(String string)
    {
        msg(C.GRAY + string);
    }

    public static void msg(String string)
    {
        try
        {
            if(instance == null)
            {
                System.out.println("[Adapt]: " + string);
                return;
            }

            String msg = C.GRAY + "[" + C.LIGHT_PURPLE + "Adapt" + C.GRAY + "]: " + string;
            Bukkit.getConsoleSender().sendMessage(msg);
        }

        catch(Throwable e)
        {
            System.out.println("[Adapt]: " + string);
        }
    }

    public static void success(String string)
    {
        msg(C.GREEN + string);
    }

    public static void info(String string)
    {
        msg(C.WHITE + string);
    }

}
