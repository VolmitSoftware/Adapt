package com.volmit.adapt.util.misc;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public interface Adventure {
    static Adventure create(Plugin plugin) {
        if (Audience.class.isAssignableFrom(Player.class)) {
            return new Adventure() {
                @Override
                public Audience player(Player p) {
                    return (Audience) p;
                }

                @Override
                public Audience sender(CommandSender sender) {
                    return (Audience) sender;
                }

                @Override
                public void close() {

                }
            };
        } else {
            BukkitAudiences bukkit = BukkitAudiences.create(plugin);
            return new Adventure() {
                @Override
                public Audience player(Player p) {
                    return bukkit.player(p);
                }

                @Override
                public Audience sender(CommandSender sender) {
                    return bukkit.sender(sender);
                }

                @Override
                public void close() {
                    bukkit.close();
                }
            };
        }
    }

    Audience player(Player p);
    Audience sender(CommandSender sender);

    void close();
}
