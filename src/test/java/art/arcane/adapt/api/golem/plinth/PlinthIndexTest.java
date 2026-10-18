package art.arcane.adapt.api.golem.plinth;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PlinthIndexTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final UUID OTHER_WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

  private final PlinthSlotItem sugar = PlinthSlotItem.of(UUID.randomUUID(), mock(ItemStack.class));
  private final PlinthSlotItem feather = PlinthSlotItem.of(UUID.randomUUID(), mock(ItemStack.class));

  @Test
  void aChunkIsNotReadyUntilItsMantleReadCompletes() {
    PlinthIndex index = new PlinthIndex();
    PlinthSpot spot = new PlinthSpot(WORLD, 1, 64, 1);

    long generation = index.beginLoad(WORLD, spot.chunkKey());

    assertThat(index.isReady(WORLD, spot.chunkKey())).isFalse();
    assertThat(index.put(spot, sugar)).isFalse();
    assertThat(index.completeLoad(WORLD, spot.chunkKey(), generation, Map.of(spot, sugar))).isTrue();
    assertThat(index.isReady(WORLD, spot.chunkKey())).isTrue();
    assertThat(index.get(spot)).contains(sugar);
  }

  @Test
  void aStaleLoadCannotOverwriteANewerOneOrAnUnloadedChunk() {
    PlinthIndex index = new PlinthIndex();
    PlinthSpot spot = new PlinthSpot(WORLD, 1, 64, 1);
    long first = index.beginLoad(WORLD, spot.chunkKey());
    long second = index.beginLoad(WORLD, spot.chunkKey());

    assertThat(index.completeLoad(WORLD, spot.chunkKey(), first, Map.of(spot, sugar))).isFalse();
    assertThat(index.isCurrent(WORLD, spot.chunkKey(), first)).isFalse();
    assertThat(index.isCurrent(WORLD, spot.chunkKey(), second)).isTrue();

    index.unload(WORLD, spot.chunkKey());

    assertThat(index.completeLoad(WORLD, spot.chunkKey(), second, Map.of(spot, sugar))).isFalse();
    assertThat(index.get(spot)).isEmpty();
  }

  @Test
  void unloadingReturnsAndForgetsTheChunksSlots() {
    PlinthIndex index = new PlinthIndex();
    PlinthSpot north = new PlinthSpot(WORLD, 1, 64, 1);
    PlinthSpot south = new PlinthSpot(WORLD, 1, 64, 5);
    PlinthSpot elsewhere = new PlinthSpot(OTHER_WORLD, 1, 64, 1);
    ready(index, WORLD, north, Map.of(north, sugar, south, feather));
    ready(index, OTHER_WORLD, elsewhere, Map.of(elsewhere, sugar));

    assertThat(index.unload(WORLD, north.chunkKey())).containsExactlyInAnyOrder(north, south);
    assertThat(index.get(south)).isEmpty();
    assertThat(index.unloadWorld(OTHER_WORLD)).containsExactly(elsewhere);
    assertThat(index.get(elsewhere)).isEmpty();
  }

  @Test
  void aGenerationUnloadOnlyForgetsThatGeneration() {
    PlinthIndex index = new PlinthIndex();
    PlinthSpot spot = new PlinthSpot(WORLD, 1, 64, 1);
    long first = index.beginLoad(WORLD, spot.chunkKey());
    long second = index.beginLoad(WORLD, spot.chunkKey());

    assertThat(index.unload(WORLD, spot.chunkKey(), first)).isFalse();
    assertThat(index.isCurrent(WORLD, spot.chunkKey(), second)).isTrue();
    assertThat(index.unload(WORLD, spot.chunkKey(), second)).isTrue();
    assertThat(index.isCurrent(WORLD, spot.chunkKey(), second)).isFalse();
  }

  @Test
  void removingReturnsTheStoredItem() {
    PlinthIndex index = new PlinthIndex();
    PlinthSpot spot = new PlinthSpot(WORLD, 1, 64, 1);
    ready(index, WORLD, spot, Map.of(spot, sugar));

    assertThat(index.remove(spot)).isEqualTo(sugar);
    assertThat(index.remove(spot)).isNull();
    assertThat(index.put(spot, feather)).isTrue();
    assertThat(index.get(spot)).contains(feather);
  }

  private static void ready(PlinthIndex index, UUID world, PlinthSpot spot, Map<PlinthSpot, PlinthSlotItem> slots) {
    index.completeLoad(world, spot.chunkKey(), index.beginLoad(world, spot.chunkKey()), slots);
  }
}
