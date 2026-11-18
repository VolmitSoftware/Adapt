package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.bukkit.platform.data.BlockDataStore;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlockDataStoreTest {
  private static final NamespacedKey VALUE = new NamespacedKey("adapt", "elevator_up");
  private static final NamespacedKey LEGACY_ENTRY = new NamespacedKey("adapt", "x1y64z13");

  private final FakeDataContainer chunkData = new FakeDataContainer();
  private Plugin plugin;
  private Block block;

  @BeforeEach
  void placeBlock() {
    plugin = mock(Plugin.class);
    when(plugin.getName()).thenReturn("Adapt");
    when(plugin.namespace()).thenReturn("adapt");
    Chunk chunk = mock(Chunk.class);
    when(chunk.getPersistentDataContainer()).thenReturn(chunkData);
    block = mock(Block.class);
    when(block.getChunk()).thenReturn(chunk);
    when(block.getX()).thenReturn(17);
    when(block.getY()).thenReturn(64);
    when(block.getZ()).thenReturn(-3);
  }

  @Test
  void blockDataLivesUnderTheChunkKeyExistingWorldsAlreadyUse() {
    BlockDataStore.edit(plugin, block, data -> data.set(VALUE, PersistentDataType.INTEGER, 4));

    assertThat(chunkData.has(LEGACY_ENTRY, PersistentDataType.TAG_CONTAINER)).isTrue();
    assertThat(BlockDataStore.has(plugin, block)).isTrue();
    PersistentDataContainer stored = BlockDataStore.read(plugin, block);
    assertThat(stored.get(VALUE, PersistentDataType.INTEGER)).isEqualTo(4);
  }

  @Test
  void removingTheLastValueDropsTheBlockEntry() {
    BlockDataStore.edit(plugin, block, data -> data.set(VALUE, PersistentDataType.INTEGER, 4));

    BlockDataStore.edit(plugin, block, data -> data.remove(VALUE));

    assertThat(chunkData.isEmpty()).isTrue();
    assertThat(BlockDataStore.read(plugin, block)).isNull();
  }

  @Test
  void clearingRemovesEveryValueOfTheBlock() {
    BlockDataStore.edit(plugin, block, data -> data.set(VALUE, PersistentDataType.INTEGER, 4));

    BlockDataStore.clear(plugin, block);

    assertThat(BlockDataStore.has(plugin, block)).isFalse();
  }
}
