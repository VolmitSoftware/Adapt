package com.volmit.adapt.api.notification;

import com.volmit.adapt.api.world.AdaptPlayer;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TitleNotification implements Notification {
    @Builder.Default
    private final long in = 250;
    @Builder.Default
    private final long stay = 1450;
    @Builder.Default
    private final long out = 750;
    @Builder.Default
    private final String title = " ";
    @Builder.Default
    private final String subtitle = " ";
    @Builder.Default
    private final String group = "default";

    @Override
    public long getTotalDuration() {
        return in + out + stay;
    }

    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public void play(AdaptPlayer p) {
        p.getPlayer().sendTitle(title.isEmpty() ? " " : title, subtitle, (int) (in / 50D), (int) (stay / 50D), (int) (out / 50D));
    }
}
