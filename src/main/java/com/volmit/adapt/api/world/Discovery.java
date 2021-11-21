package com.volmit.adapt.api.world;

import com.volmit.adapt.util.KList;
import lombok.Getter;

public class Discovery<T> {
    @Getter
    private final KList<T> seen = new KList<>();

    public boolean isNewDiscovery(T t)
    {
        return seen.addIfMissing(t);
    }
}
