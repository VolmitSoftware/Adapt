package com.volmit.adapt.nms;

import com.google.common.collect.ImmutableMap;
import com.volmit.adapt.Adapt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;

public final class NMS {

    private static final Map<String, Impl> VERSIONS = new ImmutableMap.Builder<String, Impl>()
            .put("1.19", new NMS_1_19())
            .build();

    private static String version;
    private static Impl impl;

    public static void init() {
        version = Bukkit.getBukkitVersion().split("-")[0];
        impl = VERSIONS.getOrDefault(version(), new NMS_Default());

        if (impl instanceof NMS_Default)
            Adapt.error("Failed to bind NMS for Version " + version() + "!");
        else
            Adapt.info("Successfully bound NMS for Version " + version() + ".");
    }

    public static String version() {
        return version;
    }

    public static Impl get() {
        return impl;
    }

    public interface Impl {
        void sendCooldown(Player p, Material m, int tick);
    }
}
