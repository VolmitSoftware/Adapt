package com.volmit.adapt.api.version;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class Version {
    private static final List<Entry> BINDINGS = List.of(
        of("1.20.6"),
        of("1.20.4"),
        of("1.19.2")
    );
    private static final IBindings bindings = bind();

    public static IBindings get() {
        return bindings;
    }

    private static IBindings bind() {
        Entry entry = of(Bukkit.getServer().getBukkitVersion().split("-")[0]);
        Entry version = BINDINGS.stream()
                .filter(o -> o.compareTo(entry) <= 0)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No version found for " + entry));

        try {
            Class<?> clazz = version.getClass("Bindings");
            try {
                return (IBindings) clazz.getConstructor().newInstance();
            } catch (Throwable e) {
                throw new IllegalStateException("Could not create bindings for " + entry, e);
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Could not find bindings for " + entry, e);
        }
    }

    private static Entry of(String version) {
        var s = version.split("\\.");
        return new Entry(Integer.parseInt(s[0]), Integer.parseInt(s[1]), Integer.parseInt(s[2]));
    }

    private record Entry(int major, int minor, int patch) implements Comparable<Entry> {
        public Class<?> getClass(String className) throws ClassNotFoundException {
            return Class.forName("com.volmit.adapt.api.version.v%s_%s_%s.%s".formatted(major, minor, patch, className));
        }

        @Override
        public int compareTo(@NotNull Version.Entry o) {
            int result = Integer.compare(major, o.major);
            if (result == 0) result = Integer.compare(minor, o.minor);
            if (result == 0) result = Integer.compare(patch, o.patch);
            return result;
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch;
        }
    }
}
