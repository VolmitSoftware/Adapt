package com.fren_gor.ultimateAdvancementAPI.util;

import com.fren_gor.ultimateAdvancementAPI.nms.util.ReflectionUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

public final class Versions {
    private static final String API_VERSION = "2.7.2";

    private static final List<String> SUPPORTED_NMS_VERSIONS = List.of(
            "v1_15_R1",
            "v1_16_R1",
            "v1_16_R2",
            "v1_16_R3",
            "v1_17_R1",
            "v1_18_R1",
            "v1_18_R2",
            "v1_19_R1",
            "v1_19_R2",
            "v1_19_R3",
            "v1_20_R1",
            "v1_20_R2",
            "v1_20_R3",
            "v1_20_R4",
            "v1_21_R1",
            "v1_21_R2",
            "v1_21_R3",
            "v1_21_R4",
            "v1_21_R5",
            "v1_21_R6",
            "v1_21_R7"
    );

    private static final Map<String, List<String>> NMS_TO_VERSIONS = Map.ofEntries(
            Map.entry("v1_15_R1", List.of("1.15", "1.15.1", "1.15.2")),
            Map.entry("v1_16_R1", List.of("1.16", "1.16.1", "1.16.2")),
            Map.entry("v1_16_R2", List.of("1.16.3", "1.16.4")),
            Map.entry("v1_16_R3", List.of("1.16.5")),
            Map.entry("v1_17_R1", List.of("1.17", "1.17.1")),
            Map.entry("v1_18_R1", List.of("1.18", "1.18.1")),
            Map.entry("v1_18_R2", List.of("1.18.2")),
            Map.entry("v1_19_R1", List.of("1.19", "1.19.2")),
            Map.entry("v1_19_R2", List.of("1.19.3")),
            Map.entry("v1_19_R3", List.of("1.19.4")),
            Map.entry("v1_20_R1", List.of("1.20", "1.20.1")),
            Map.entry("v1_20_R2", List.of("1.20.2")),
            Map.entry("v1_20_R3", List.of("1.20.3", "1.20.4")),
            Map.entry("v1_20_R4", List.of("1.20.5", "1.20.6")),
            Map.entry("v1_21_R1", List.of("1.21", "1.21.1")),
            Map.entry("v1_21_R2", List.of("1.21.2", "1.21.3")),
            Map.entry("v1_21_R3", List.of("1.21.4")),
            Map.entry("v1_21_R4", List.of("1.21.5")),
            Map.entry("v1_21_R5", List.of("1.21.6", "1.21.7", "1.21.8")),
            Map.entry("v1_21_R6", List.of("1.21.9", "1.21.10")),
            Map.entry("v1_21_R7", List.of("1.21.11", "26.1", "26.2"))
    );

    private static final Map<String, String> NMS_TO_FANCY = Map.ofEntries(
            Map.entry("v1_15_R1", "1.15-1.15.2"),
            Map.entry("v1_16_R1", "1.16-1.16.2"),
            Map.entry("v1_16_R2", "1.16.3-1.16.4"),
            Map.entry("v1_16_R3", "1.16.5"),
            Map.entry("v1_17_R1", "1.17-1.17.1"),
            Map.entry("v1_18_R1", "1.18-1.18.1"),
            Map.entry("v1_18_R2", "1.18.2"),
            Map.entry("v1_19_R1", "1.19-1.19.2"),
            Map.entry("v1_19_R2", "1.19.3"),
            Map.entry("v1_19_R3", "1.19.4"),
            Map.entry("v1_20_R1", "1.20-1.20.1"),
            Map.entry("v1_20_R2", "1.20.2"),
            Map.entry("v1_20_R3", "1.20.3-1.20.4"),
            Map.entry("v1_20_R4", "1.20.5-1.20.6"),
            Map.entry("v1_21_R1", "1.21-1.21.1"),
            Map.entry("v1_21_R2", "1.21.2-1.21.3"),
            Map.entry("v1_21_R3", "1.21.4"),
            Map.entry("v1_21_R4", "1.21.5"),
            Map.entry("v1_21_R5", "1.21.6-1.21.8"),
            Map.entry("v1_21_R6", "1.21.9-1.21.10"),
            Map.entry("v1_21_R7", "1.21.11, 26.1-26.2")
    );

    private static final List<String> SUPPORTED_VERSIONS = SUPPORTED_NMS_VERSIONS.stream()
            .flatMap(s -> NMS_TO_VERSIONS.get(s).stream())
            .toList();

    @Nullable
    private static Optional<String> COMPLETE_VERSION;

    private Versions() {
        throw new UnsupportedOperationException("Utility class.");
    }

    public static String normalizeMinecraftVersion(String version) {
        if (version == null || version.isBlank()) {
            return version;
        }
        if ("26.2".equals(version) || version.startsWith("26.2.") || version.startsWith("26.2-")
                || "26.1".equals(version) || version.startsWith("26.1.") || version.startsWith("26.1-")) {
            return "1.21.11";
        }
        return version;
    }

    @NotNull
    public static Optional<String> getNMSVersion() {
        if (COMPLETE_VERSION != null) {
            return COMPLETE_VERSION;
        }

        String compatibleVersion = normalizeMinecraftVersion(ReflectionUtil.MINECRAFT_VERSION);
        String version = NMS_TO_VERSIONS.entrySet().stream()
                .filter(e -> e.getValue().contains(compatibleVersion))
                .map(Entry::getKey)
                .findFirst()
                .orElse(null);

        COMPLETE_VERSION = version != null ? Optional.of(version) : Optional.empty();
        return COMPLETE_VERSION;
    }

    public static String getApiVersion() {
        return API_VERSION;
    }

    @UnmodifiableView
    @NotNull
    public static List<@NotNull String> getSupportedVersions() {
        return SUPPORTED_VERSIONS;
    }

    @UnmodifiableView
    @NotNull
    public static List<@NotNull String> getSupportedNMSVersions() {
        return SUPPORTED_NMS_VERSIONS;
    }

    @Nullable
    public static String getNMSVersionsRange() {
        return getNMSVersion().map(Versions::getNMSVersionsRange).orElse(null);
    }

    @Nullable
    @Contract("null -> null")
    public static String getNMSVersionsRange(String version) {
        return NMS_TO_FANCY.get(version);
    }

    @UnmodifiableView
    @Nullable
    public static List<@NotNull String> getNMSVersionsList() {
        return getNMSVersion().map(Versions::getNMSVersionsList).orElse(null);
    }

    @UnmodifiableView
    @Nullable
    @Contract("null -> null")
    public static List<@NotNull String> getNMSVersionsList(String version) {
        return NMS_TO_VERSIONS.get(version);
    }

    @Contract("null -> null; !null -> !null")
    public static String removeInitialV(String string) {
        return string == null || string.isEmpty() || string.charAt(0) != 'v' ? string : string.substring(1);
    }
}
