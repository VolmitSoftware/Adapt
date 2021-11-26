package com.volmit.adapt.api.notification;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.world.AdaptPlayer;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ActionBarNotification implements Notification {
    @Builder.Default
    private final long duration = 3000;
    @Builder.Default
    private final String title = " ";
    @Builder.Default
    private final String group = "default";

    @Override
    public long getTotalDuration() {
        return duration;
    }

    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public void play(AdaptPlayer p) {
        Adapt.actionbar(p.getPlayer(), title);
    }
}
