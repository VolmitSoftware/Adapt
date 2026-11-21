package art.arcane.adapt.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * {@code config/adapt-client.json}: one JSON object per named section, each bound to a settings class with a public
 * no-argument constructor whose field initializers are the defaults. Missing sections and fields take their defaults;
 * unknown sections and fields are kept on save.
 */
public final class ClientConfig {
    public static final String FILE_NAME = "adapt-client.json";
    public static final String UI = "ui";
    private static final Logger LOGGER = LoggerFactory.getLogger("Adapt");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;
    private final JsonObject stored;
    private final Map<String, Object> sections = new LinkedHashMap<>();

    private ClientConfig(Path file, JsonObject stored) {
        this.file = file;
        this.stored = stored;
    }

    /** Reads {@code file}; a missing or unreadable file gives every section its defaults. */
    public static ClientConfig load(Path file) {
        Objects.requireNonNull(file, "file");
        if (!Files.isRegularFile(file)) {
            return new ClientConfig(file, new JsonObject());
        }
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            return new ClientConfig(file, parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject());
        } catch (IOException | JsonParseException failure) {
            LOGGER.warn("Could not read {}, using defaults: {}", file, failure.getMessage());
            return new ClientConfig(file, new JsonObject());
        }
    }

    /** A config that is never written, for tests and for clients without a config directory. */
    public static ClientConfig memory() {
        return new ClientConfig(null, new JsonObject());
    }

    public UiSettings ui() {
        return section(UI, UiSettings.class);
    }

    /** The settings of section {@code name}, read once and then shared; change them and call {@link #save()}. */
    public synchronized <T> T section(String name, Class<T> type) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Object cached = sections.get(name);
        if (cached != null) {
            if (!type.isInstance(cached)) {
                throw new IllegalArgumentException("Section " + name + " is bound to " + cached.getClass().getName());
            }
            return type.cast(cached);
        }
        T section = read(name, type);
        sections.put(name, section);
        return section;
    }

    /** Writes every section read so far, keeping what this version does not know. */
    public synchronized void save() {
        for (Map.Entry<String, Object> entry : sections.entrySet()) {
            JsonObject merged = GSON.toJsonTree(entry.getValue()).getAsJsonObject();
            JsonElement previous = stored.get(entry.getKey());
            if (previous != null && previous.isJsonObject()) {
                for (Map.Entry<String, JsonElement> field : previous.getAsJsonObject().entrySet()) {
                    if (!merged.has(field.getKey())) {
                        merged.add(field.getKey(), field.getValue());
                    }
                }
            }
            stored.add(entry.getKey(), merged);
        }
        if (file == null) {
            return;
        }
        try {
            Path parent = file.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Path temporary = Files.createTempFile(parent, FILE_NAME, ".tmp");
            Files.writeString(temporary, GSON.toJson(stored), StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            LOGGER.warn("Could not write {}: {}", file, failure.getMessage());
        }
    }

    private <T> T read(String name, Class<T> type) {
        JsonElement element = stored.get(name);
        if (element != null && element.isJsonObject()) {
            try {
                T parsed = GSON.fromJson(element, type);
                if (parsed != null) {
                    return parsed;
                }
            } catch (JsonParseException failure) {
                LOGGER.warn("Section {} of {} is invalid, using defaults: {}", name, FILE_NAME, failure.getMessage());
            }
        }
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException failure) {
            throw new IllegalArgumentException(type.getName() + " needs a public no-argument constructor", failure);
        }
    }
}
