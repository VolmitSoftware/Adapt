package com.volmit.adapt.api.notification;

import org.bukkit.entity.Player;

public interface Notification {
    public long getTotalDuration();

    public void play(Player p);
}
