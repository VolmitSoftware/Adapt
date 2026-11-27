package art.arcane.adapt.pack.runtime;

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.pack.bake.PackManifest;
import art.arcane.adapt.pack.bake.PackValidator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Built packs on disk, one folder per input key holding {@code pack.zip} and {@code manifest.json}. A cached pack is
 * validated again when read; one that fails is deleted so it gets baked anew.
 */
public final class PackCache {
    public static final String ZIP = "pack.zip";
    public static final String MANIFEST = "manifest.json";
    private static final String STAGING = ".staging-";

    private final Path directory;

    public PackCache(Path directory) {
        this.directory = directory;
    }

    /** A pack and its manifest JSON. */
    public record Entry(byte[] zip, String manifest) {
    }

    public Path directory() {
        return directory;
    }

    public Optional<Entry> load(String key) {
        Path folder = directory.resolve(key);
        Path zip = folder.resolve(ZIP);
        Path manifest = folder.resolve(MANIFEST);
        if (!Files.isRegularFile(zip) || !Files.isRegularFile(manifest)) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(zip);
            String json = Files.readString(manifest, StandardCharsets.UTF_8);
            PackManifest.fromJson(json);
            List<String> problems = PackValidator.check(unzip(bytes));
            if (problems.isEmpty()) {
                return Optional.of(new Entry(bytes, json));
            }
            AdaptLog.warn("Discarding the cached Adapt pack " + key + ": " + problems.get(0));
        } catch (IOException | RuntimeException broken) {
            AdaptLog.warn("Discarding the cached Adapt pack " + key + ": " + broken);
        }
        delete(folder);
        return Optional.empty();
    }

    public void store(String key, Entry entry) throws IOException {
        Files.createDirectories(directory);
        Path staged = Files.createTempDirectory(directory, STAGING);
        try {
            Files.write(staged.resolve(ZIP), entry.zip());
            Files.writeString(staged.resolve(MANIFEST), entry.manifest(), StandardCharsets.UTF_8);
            Files.move(staged, directory.resolve(key), StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException present) {
            AdaptLog.debug("Adapt pack " + key + " was already cached.");
        } finally {
            delete(staged);
        }
    }

    /** Deletes every cached pack but {@code keep}. Folders still being written are left alone. */
    public void prune(String keep) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> folders = Files.list(directory)) {
            for (Path folder : folders.toList()) {
                String name = folder.getFileName().toString();
                if (!name.equals(keep) && !name.startsWith(STAGING)) {
                    delete(folder);
                }
            }
        } catch (IOException failure) {
            AdaptLog.warn("Could not prune the Adapt pack cache " + directory + ": " + failure.getMessage());
        }
    }

    static Map<String, byte[]> unzip(byte[] zip) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    files.put(entry.getName(), in.readAllBytes());
                }
            }
        }
        return files;
    }

    private static void delete(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path each : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(each);
            }
        } catch (IOException failure) {
            AdaptLog.warn("Could not delete " + path + ": " + failure.getMessage());
        }
    }
}
