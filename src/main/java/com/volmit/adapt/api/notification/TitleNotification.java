package com.volmit.adapt.api.notification;

import lombok.Builder;
import org.bukkit.entity.Player;

@Builder
public class TitleNotification implements Notification{
    @Builder.Default
    private long in = 250;
    @Builder.Default
    private long stay = 1450;
    @Builder.Default
    private long out = 750;
    @Builder.Default
    private String title = "";
    @Builder.Default
    private String subtitle = "";

    @Override
    public long getTotalDuration() {
        return in + out + stay;
    }

    @Override
    public void play(Player p) {
        p.sendTitle(title, subtitle, (int)(in / 50D), (int)(stay / 50D), (int)(out / 50D));
    }
}
