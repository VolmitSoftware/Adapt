package com.volmit.adapt.api.notification;

import lombok.Builder;
import org.bukkit.entity.Player;

@Builder
public class TitleNotification implements Notification{
    @Builder.Default
    private final long in = 250;
    @Builder.Default
    private final long stay = 1450;
    @Builder.Default
    private final long out = 750;
    @Builder.Default
    private final String title = "";
    @Builder.Default
    private final String subtitle = "";

    @Override
    public long getTotalDuration() {
        return in + out + stay;
    }

    @Override
    public void play(Player p) {
        p.sendTitle(title, subtitle, (int)(in / 50D), (int)(stay / 50D), (int)(out / 50D));
    }
}
