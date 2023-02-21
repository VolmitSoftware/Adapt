package com.volmit.adapt.api.protection;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;

public class DefaultProtectors {
    private static final List<Protector> protectors = new ArrayList<>();

    public static void registerProtector(Protector protector) {
        protectors.add(protector);
    }

    public static void unregisterProtector(Protector protector) {
        protectors.remove(protector);
    }

    public static List<Protector> getDefaultProtectors() {
        return ImmutableList.copyOf(protectors);
    }
}
