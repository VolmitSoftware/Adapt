package art.arcane.adapt.client.items;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemBasesTest {
    @TempDir
    Path directory;

    @Test
    void savedIdsLoadBackSortedOnePerLine() throws IOException {
        Path file = directory.resolve("config").resolve("adapt").resolve("viewer-items.txt");
        ItemBases bases = new ItemBases(file);

        assertTrue(bases.save(Set.of("minecraft:stick", "minecraft:snowball")));

        assertEquals(List.of("minecraft:snowball", "minecraft:stick"), Files.readAllLines(file, StandardCharsets.UTF_8));
        assertEquals(Set.of("minecraft:snowball", "minecraft:stick"), bases.load());
    }

    @Test
    void aMissingFileIsNoIdsAndBlankLinesAreSkipped() throws IOException {
        Path file = directory.resolve("viewer-items.txt");
        assertEquals(Set.of(), new ItemBases(file).load());

        Files.writeString(file, "minecraft:bundle\n\n  \nminecraft:stick\n", StandardCharsets.UTF_8);

        assertEquals(Set.of("minecraft:bundle", "minecraft:stick"), new ItemBases(file).load());
    }
}
