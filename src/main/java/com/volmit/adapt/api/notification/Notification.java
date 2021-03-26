package com.volmit.adapt.api.notification;

import org.bukkit.entity.Player;

public interface Notification {
    long getTotalDuration();

    void play(Player p);
}
