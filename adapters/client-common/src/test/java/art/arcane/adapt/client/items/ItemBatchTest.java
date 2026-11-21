package art.arcane.adapt.client.items;

import art.arcane.adapt.client.protocol.ItemDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemBatchTest {
    @Test
    void chunksJoinInOrderWhenTheEmptyFrameArrives() {
        ItemBatch batch = new ItemBatch();

        assertTrue(batch.accept(List.of(item("adapt:a"), item("adapt:b"))).isEmpty());
        assertTrue(batch.accept(List.of(item("adapt:c"))).isEmpty());
        Optional<List<ItemDescriptor>> complete = batch.accept(List.of());

        assertEquals(List.of(item("adapt:a"), item("adapt:b"), item("adapt:c")), complete.orElseThrow());
        assertEquals(List.of(item("adapt:d")), joined(batch, List.of(item("adapt:d"))));
    }

    @Test
    void anEmptyFrameAloneIsAnEmptyList() {
        assertEquals(List.of(), new ItemBatch().accept(List.of()).orElseThrow());
    }

    @Test
    void clearDropsAPartialList() {
        ItemBatch batch = new ItemBatch();
        batch.accept(List.of(item("adapt:a")));

        batch.clear();

        assertEquals(List.of(item("adapt:b")), joined(batch, List.of(item("adapt:b"))));
    }

    private static List<ItemDescriptor> joined(ItemBatch batch, List<ItemDescriptor> chunk) {
        batch.accept(chunk);
        return batch.accept(List.of()).orElseThrow();
    }

    private static ItemDescriptor item(String id) {
        return new ItemDescriptor(id, "minecraft:snowball", id, List.of(), null, 0, new byte[] {1, 2, 3});
    }
}
