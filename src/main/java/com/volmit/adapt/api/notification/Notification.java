package com.volmit.adapt.api.notification;

import com.volmit.adapt.api.world.AdaptPlayer;

public interface Notification {
    String DEFAULT_GROUP = "default";

    long getTotalDuration();

    void play(AdaptPlayer p);

    default String getGroup() {
        return DEFAULT_GROUP;
    }
}
