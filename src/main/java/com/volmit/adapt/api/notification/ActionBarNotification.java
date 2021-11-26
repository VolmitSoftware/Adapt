package com.volmit.adapt.api.notification;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.M;
import io.papermc.lib.PaperLib;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ActionBarNotification implements Notification {
    @Builder.Default
    private final long duration = 750;
    @Builder.Default
    private final String title = " ";
    @Builder.Default
    private final String group = "default";
    @Builder.Default
    private final long maxTTL = Long.MAX_VALUE;

    @Override
    public long getTotalDuration() {
        if(M.ms() > maxTTL) {
            return 0;
        }
        return duration;
    }

    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public void play(AdaptPlayer p) {
        if(M.ms() > maxTTL) {
            return;
        }

        Adapt.actionbar(p.getPlayer(), title);
    }
}
