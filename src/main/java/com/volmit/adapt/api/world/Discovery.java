package com.volmit.adapt.api.world;

import com.volmit.adapt.util.KList;
import lombok.Getter;

public class Discovery<T> {
    @Getter
    private KList<T> seen = new KList<>();

    public boolean isNewDiscovery(T t)
    {
        return seen.addIfMissing(t);
    }

    public double getPower()
    {
        double s = seen.size();
        return (s*0.15);
    }
}
