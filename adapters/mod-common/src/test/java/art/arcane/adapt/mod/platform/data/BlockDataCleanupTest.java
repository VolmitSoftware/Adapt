package art.arcane.adapt.mod.platform.data;

import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockExplodeEvent;
import art.arcane.adapt.platform.world.BlockRef;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Block data outlives every handler of the break that removes its block, and only real removals drop it. */
class BlockDataCleanupTest {
    private static final DataKey<String> OWNER = DataKey.adapt("scarecrow_owner", DataCodecs.STRING);

    private static final class MemoryStore implements NbtDataHolder.Store {
        private CompoundTag tag;

        @Override
        public CompoundTag read() {
            return tag;
        }

        @Override
        public void write(NbtDataHolder.Edit edit) {
            CompoundTag next = tag == null ? new CompoundTag() : tag;
            edit.apply(next);
            tag = next.isEmpty() ? null : next;
        }
    }

    @Test
    void aMonitorHandlerOfTheBreakStillReadsTheBlocksDataAndItIsGoneAfterwards() {
        BlockRef block = block("owner-a");
        AdaptEventBus bus = new AdaptEventBus((Object owner, AdaptEvent event, Throwable error) -> {
            throw new AssertionError(error);
        });
        List<String> seen = new ArrayList<>();
        bus.register(this, BlockBreakEvent.class, EventPriority.MONITOR, (BlockBreakEvent event) -> seen.add(event.block().data().get(OWNER)));

        BlockBreakEvent broken = bus.post(new BlockBreakEvent(player(), block, true, 0));
        BlockDataCleanup.settled(broken);

        assertEquals(List.of("owner-a"), seen);
        assertNull(block.data().get(OWNER));
    }

    @Test
    void probesAndCancelledBreaksKeepTheData() {
        BlockRef probed = block("owner-b");
        BlockRef refused = block("owner-c");
        BlockBreakEvent probe = new BlockBreakEvent(player(), probed, true, 0);
        probe.markProtectionProbe();
        BlockBreakEvent cancelled = new BlockBreakEvent(player(), refused, true, 0);
        cancelled.setCancelled(true);

        BlockDataCleanup.settled(probe);
        BlockDataCleanup.settled(cancelled);

        assertEquals("owner-b", probed.data().get(OWNER));
        assertEquals("owner-c", refused.data().get(OWNER));
    }

    @Test
    void anExplosionDropsTheDataOfEveryBlockItRemoves() {
        BlockRef first = block("owner-d");
        BlockRef second = block("owner-e");

        BlockDataCleanup.settled(new BlockExplodeEvent(first, null, List.of(first, second), 1F));

        assertNull(first.data().get(OWNER));
        assertNull(second.data().get(OWNER));
    }

    private static BlockRef block(String owner) {
        NbtDataHolder data = new NbtDataHolder(new MemoryStore());
        data.set(OWNER, owner);
        return (BlockRef) Proxy.newProxyInstance(BlockRef.class.getClassLoader(), new Class<?>[]{BlockRef.class},
            (Object proxy, java.lang.reflect.Method method, Object[] arguments) -> switch (method.getName()) {
                case "data" -> data;
                case "hasData" -> !data.isEmpty();
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static PlayerRef player() {
        return (PlayerRef) Proxy.newProxyInstance(PlayerRef.class.getClassLoader(), new Class<?>[]{PlayerRef.class},
            (Object proxy, java.lang.reflect.Method method, Object[] arguments) -> {
                throw new UnsupportedOperationException(method.getName());
            });
    }
}
