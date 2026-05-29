package com.fren_gor.ultimateAdvancementAPI.nms.util;

import com.fren_gor.ultimateAdvancementAPI.util.Versions;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

public final class ReflectionUtil {
    public static final String MINECRAFT_VERSION = Bukkit.getBukkitVersion().split("-")[0];
    private static final String COMPATIBLE_MINECRAFT_VERSION = Versions.normalizeMinecraftVersion(MINECRAFT_VERSION);
    public static final int MINOR_VERSION = resolveMinorVersion(COMPATIBLE_MINECRAFT_VERSION);
    private static final String CRAFTBUKKIT_PACKAGE = Bukkit.getServer().getClass().getPackage().getName();
    public static final int VERSION = resolveVersion(COMPATIBLE_MINECRAFT_VERSION);
    private static final boolean IS_1_17 = VERSION >= 17;

    private ReflectionUtil() {
        throw new UnsupportedOperationException("Utility class.");
    }

    private static int resolveVersion(String version) {
        String[] split = version.split("\\.");
        if (split.length >= 2 && "1".equals(split[0])) {
            return Integer.parseInt(split[1]);
        }
        if (split.length >= 1) {
            return Integer.parseInt(split[0]);
        }
        return 0;
    }

    private static int resolveMinorVersion(String version) {
        String[] split = version.split("\\.");
        if (split.length >= 3 && "1".equals(split[0])) {
            return Integer.parseInt(split[2]);
        }
        if (split.length >= 2 && !"1".equals(split[0])) {
            return Integer.parseInt(split[1]);
        }
        return 0;
    }

    public static boolean classExists(@NotNull String className) {
        Objects.requireNonNull(className, "ClassName cannot be null.");
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Nullable
    public static Class<?> getNMSClass(@NotNull String name, @NotNull String mcPackage) {
        String path;
        if (IS_1_17) {
            path = "net.minecraft." + mcPackage + '.' + name;
        } else {
            Optional<String> version = Versions.getNMSVersion();
            if (version.isEmpty()) {
                Bukkit.getLogger().severe("[UltimateAdvancementAPI] Unsupported Minecraft version! (" + MINECRAFT_VERSION + ")");
                return null;
            }
            path = "net.minecraft.server." + version.get() + '.' + name;
        }

        try {
            return Class.forName(path);
        } catch (ClassNotFoundException e) {
            Bukkit.getLogger().severe("[UltimateAdvancementAPI] Can't find NMS Class! (" + path + ")");
            return null;
        }
    }

    @Nullable
    public static Class<?> getCBClass(@NotNull String name) {
        String cb = CRAFTBUKKIT_PACKAGE + "." + name;
        try {
            return Class.forName(cb);
        } catch (ClassNotFoundException e) {
            Bukkit.getLogger().severe("[UltimateAdvancementAPI] Can't find CB Class! (" + cb + ")");
            return null;
        }
    }

    @Nullable
    public static <T> Class<? extends T> getWrapperClass(@NotNull Class<T> clazz) {
        Optional<String> version = Versions.getNMSVersion();
        if (version.isEmpty()) {
            Bukkit.getLogger().severe("[UltimateAdvancementAPI] Unsupported Minecraft version! (" + MINECRAFT_VERSION + ")");
            return null;
        }

        String name = clazz.getName();
        String validPackage = "com.fren_gor.ultimateAdvancementAPI.nms.wrappers.";
        if (!name.startsWith(validPackage)) {
            throw new IllegalArgumentException("Invalid class " + name + '.');
        }
        String wrapper = "com.fren_gor.ultimateAdvancementAPI.nms." + version.get() + "." + name.substring(validPackage.length()) + '_' + version.get();
        try {
            return Class.forName(wrapper).asSubclass(clazz);
        } catch (ClassNotFoundException e) {
            Bukkit.getLogger().severe("[UltimateAdvancementAPI] Can't find Wrapper Class! (" + wrapper + ")");
            return null;
        }
    }
}
