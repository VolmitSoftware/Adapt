package com.volmit.adapt.api.notification;

import org.bukkit.entity.Player;

public interface Notification {
    String DEFAULT_GROUP = "default";

    long getTotalDuration();

    void play(Player p);

    default String getGroup()
    {
        return DEFAULT_GROUP;
    }
}
