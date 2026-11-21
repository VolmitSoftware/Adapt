package art.arcane.adapt.client.hud;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/** The layout file, {@code config/adapt-hud.json} under the game directory. Writes go through a temporary file. */
public final class HudLayoutStore {
    private final Path file;

    public HudLayoutStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    public static HudLayoutStore inGameDirectory() {
        return new HudLayoutStore(Path.of("config", "adapt-hud.json"));
    }

    public Path file() {
        return file;
    }

    /** The saved layout, or the defaults when there is no file or it cannot be read. */
    public HudLayout load() {
        try {
            return HudLayoutCodec.read(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException unreadable) {
            return HudDefaults.layout();
        }
    }

    /** When the file last changed, in epoch milliseconds, or -1 when it does not exist or cannot be read. */
    public long modified() {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException | RuntimeException missing) {
            return -1L;
        }
    }

    public void save(HudLayout layout) throws IOException {
        Path absolute = file.toAbsolutePath();
        Path directory = absolute.getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, absolute.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, HudLayoutCodec.write(layout), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
