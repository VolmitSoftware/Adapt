package com.volmit.adapt.api.protection;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ProtectorRegistry {
    private static final List<Protector> protectors = new ArrayList<>();

    public static void registerProtector(Protector protector) {
        protectors.add(protector);
    }

    public static void unregisterProtector(Protector protector) {
        protectors.remove(protector);
    }

    public static List<Protector> getDefaultProtectors() {
        return protectors.stream().filter(Protector::isEnabledByDefault).collect(ImmutableList.toImmutableList());
    }

    public static List<Protector> getAllProtectors() {
        return ImmutableList.copyOf(protectors);
    }
}
