package com.volmit.adapt.api.world;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class Discovery<T> {
    @Getter
    private final List<T> seen = new ArrayList<>();

    public boolean isNewDiscovery(T t) {
        return seen.addIfMissing(t);
    }
}
