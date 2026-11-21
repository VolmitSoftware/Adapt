package art.arcane.adapt.client.items;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The base item ids of every Adapt item this client has seen, one per line. JEI fixes its subtype interpreters when it
 * starts, which on join happens before the server's list arrives, so JEI registers them for the ids remembered here.
 */
final class ItemBases {
    private final Path file;

    ItemBases(Path file) {
        this.file = file;
    }

    Set<String> load() {
        Set<String> ids = new TreeSet<>();
        if (!Files.isRegularFile(file)) {
            return ids;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String id = line.trim();
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            }
        } catch (IOException unreadable) {
            return new TreeSet<>();
        }
        return ids;
    }

    /** Writes {@code ids} sorted; false when the file could not be written, which the next list retries. */
    boolean save(Set<String> ids) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, List.copyOf(new TreeSet<>(ids)), StandardCharsets.UTF_8);
            return true;
        } catch (IOException failed) {
            return false;
        }
    }
}
