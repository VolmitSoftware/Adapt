package art.arcane.adapt.util.config;

import com.google.gson.JsonElement;
import art.arcane.adapt.Adapt;
import art.arcane.adapt.util.project.config.ConfigRewriteReporter;
import art.arcane.volmlib.util.io.IO;
import art.arcane.adapt.util.common.io.Json;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;

public final class ConfigFileSupport {
    private static final long MAX_CONFIG_BYTES_DEFAULT = 2L * 1024L * 1024L;
    private static final long MAX_CONFIG_BYTES_SKILL_OR_ADAPTATION = 256L * 1024L;

    private ConfigFileSupport() {
    }

    public static <T> T load(
            File canonicalFile,
            File legacyFile,
            Class<T> type,
            T fallback,
            boolean overwriteOnReadFailure,
            String sourceTag,
            String createdMessage
    ) throws IOException {
        long maxConfigBytes = maxConfigBytesForSourceTag(sourceTag);
        boolean canonicalizeExisting = shouldCanonicalizeExisting(sourceTag, overwriteOnReadFailure);
        if (canonicalFile != null && canonicalFile.exists()) {
            try {
                if (canonicalFile.length() > maxConfigBytes) {
                    throw new IOException("Config file is too large (" + canonicalFile.length() + " bytes)");
                }
                String raw = IO.readAll(canonicalFile);
                T loaded = deserialize(raw, canonicalFile, type);
                if (loaded == null) {
                    throw new IOException("Config parser returned null.");
                }

                if (canonicalizeExisting) {
                    String canonical = serialize(loaded, canonicalFile, sourceTag);
                    if (!normalize(canonical).equals(normalize(raw))) {
                        ConfigRewriteReporter.reportRewrite(canonicalFile, sourceTag, raw, canonical);
                        IO.writeAll(canonicalFile, canonical);
                    }
                }
                deleteLegacyFileIfMigrated(canonicalFile, legacyFile, sourceTag);
                return loaded;
            } catch (Throwable e) {
                if (overwriteOnReadFailure) {
                    ConfigRewriteReporter.reportFallbackRewrite(canonicalFile, sourceTag, reason("invalid config", e));
                    IO.writeAll(canonicalFile, serialize(fallback, canonicalFile, sourceTag));
                    return fallback;
                }

                throw new IOException("Invalid config", e);
            }
        }

        if (legacyFile != null && legacyFile.exists()) {
            try {
                if (legacyFile.length() > maxConfigBytes) {
                    throw new IOException("Legacy config file is too large (" + legacyFile.length() + " bytes)");
                }
                String raw = IO.readAll(legacyFile);
                T loaded = deserialize(raw, legacyFile, type);
                if (loaded == null) {
                    throw new IOException("Config parser returned null.");
                }

                IO.writeAll(canonicalFile, serialize(loaded, canonicalFile, sourceTag));
                Adapt.info("Migrated legacy config [" + legacyPath(legacyFile) + "] -> [" + legacyPath(canonicalFile) + "].");
                deleteLegacyFileIfMigrated(canonicalFile, legacyFile, sourceTag);
                return loaded;
            } catch (Throwable e) {
                if (overwriteOnReadFailure) {
                    ConfigRewriteReporter.reportFallbackRewrite(canonicalFile, sourceTag, reason("invalid legacy config", e));
                    IO.writeAll(canonicalFile, serialize(fallback, canonicalFile, sourceTag));
                    return fallback;
                }

                throw new IOException("Invalid legacy config", e);
            }
        }

        IO.writeAll(canonicalFile, serialize(fallback, canonicalFile, sourceTag));
        if (createdMessage != null && !createdMessage.isBlank()) {
            Adapt.info(createdMessage);
        }
        return fallback;
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").stripTrailing();
    }

    public static File toTomlFile(File file) {
        if (file == null) {
            return null;
        }
        return replaceExtension(file, ".toml");
    }

    public static File toJsonFile(File file) {
        if (file == null) {
            return null;
        }
        return replaceExtension(file, ".json");
    }

    public static File replaceExtension(File file, String extension) {
        String name = file.getName();
        int idx = name.lastIndexOf('.');
        String base = idx >= 0 ? name.substring(0, idx) : name;
        return new File(file.getParentFile(), base + extension);
    }

    public static boolean isTomlFile(File file) {
        return file != null && file.getName().toLowerCase(Locale.ROOT).endsWith(".toml");
    }

    public static boolean isJsonFile(File file) {
        if (file == null) {
            return false;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".json") || name.endsWith(".yml") || name.endsWith(".yaml");
    }

    public static boolean isSupportedConfigFile(File file) {
        return isTomlFile(file) || isJsonFile(file);
    }

    public static String configNameFromFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".toml")) {
            return fileName.substring(0, fileName.length() - 5).toLowerCase(Locale.ROOT);
        }
        if (lower.endsWith(".json")) {
            return fileName.substring(0, fileName.length() - 5).toLowerCase(Locale.ROOT);
        }
        if (lower.endsWith(".yml")) {
            return fileName.substring(0, fileName.length() - 4).toLowerCase(Locale.ROOT);
        }
        if (lower.endsWith(".yaml")) {
            return fileName.substring(0, fileName.length() - 5).toLowerCase(Locale.ROOT);
        }
        return null;
    }

    public static JsonElement parseToJsonElement(String raw, File file) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            if (isTomlFile(file)) {
                return TomlCodec.toJsonElement(raw);
            }

            return Json.fromJson(raw, JsonElement.class);
        } catch (Throwable ignored) {
        }

        try {
            return TomlCodec.toJsonElement(raw);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String serializeJsonElementToToml(JsonElement element) {
        return TomlCodec.toToml(element);
    }

    public static boolean deleteLegacyFileIfMigrated(File canonicalFile, File legacyFile, String sourceTag) {
        if (canonicalFile == null || legacyFile == null) {
            return false;
        }
        if (!canonicalFile.exists() || !canonicalFile.isFile() || !legacyFile.exists() || !legacyFile.isFile()) {
            return false;
        }
        if (canonicalFile.getAbsoluteFile().equals(legacyFile.getAbsoluteFile())) {
            return false;
        }

        try {
            boolean deleted = Files.deleteIfExists(legacyFile.toPath());
            if (deleted) {
                Adapt.verbose("Deleted migrated legacy config [" + legacyPath(legacyFile) + "] for [" + (sourceTag == null ? "config" : sourceTag) + "].");
            }
            return deleted;
        } catch (Throwable e) {
            Adapt.warn("Failed to delete migrated legacy config [" + legacyPath(legacyFile) + "]: " + e.getMessage());
            return false;
        }
    }

    private static <T> T deserialize(String raw, File sourceFile, Class<T> type) throws IOException {
        try {
            if (isTomlFile(sourceFile)) {
                return TomlCodec.fromToml(raw, type);
            }
            return Json.fromJson(raw, type);
        } catch (Throwable e) {
            throw new IOException("Failed to parse " + sourceFile.getName(), e);
        }
    }

    private static String serialize(Object loaded, File targetFile, String sourceTag) {
        if (isTomlFile(targetFile)) {
            return TomlCodec.toToml(loaded, sourceTag);
        }
        return Json.toJson(loaded, true);
    }

    private static long maxConfigBytesForSourceTag(String sourceTag) {
        if (sourceTag != null && (sourceTag.startsWith("skill:") || sourceTag.startsWith("adaptation:"))) {
            return MAX_CONFIG_BYTES_SKILL_OR_ADAPTATION;
        }
        return MAX_CONFIG_BYTES_DEFAULT;
    }

    private static boolean shouldCanonicalizeExisting(String sourceTag, boolean overwriteOnReadFailure) {
        if (sourceTag == null) {
            return true;
        }

        // During initial startup of skill/adaptation content we prioritize fast parse/load.
        // Canonical rewrites still occur via explicit canonicalization/hotload paths.
        if (overwriteOnReadFailure && (sourceTag.startsWith("skill:") || sourceTag.startsWith("adaptation:"))) {
            return false;
        }

        return true;
    }

    private static String reason(String prefix, Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return prefix;
        }
        return prefix + ": " + error.getMessage();
    }

    private static String legacyPath(File file) {
        if (file == null) {
            return "<unknown>";
        }
        try {
            File dataFolder = Adapt.instance == null ? null : Adapt.instance.getDataFolder();
            if (dataFolder == null) {
                return file.getPath();
            }
            return dataFolder.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
        } catch (Throwable ignored) {
            return file.getPath();
        }
    }
}
