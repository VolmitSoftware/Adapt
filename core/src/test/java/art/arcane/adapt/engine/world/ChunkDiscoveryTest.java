package art.arcane.adapt.engine.world;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkDiscoveryTest {
  private static final UUID WORLD = UUID.randomUUID();

  @Test
  void eachChunkIsNewOnlyOnce() {
    ChunkDiscovery discovery = new ChunkDiscovery();
    assertThat(discovery.isNewDiscovery(WORLD, 3, -7)).isTrue();
    assertThat(discovery.isNewDiscovery(WORLD, 3, -7)).isFalse();
    assertThat(discovery.isNewDiscovery(WORLD, 4, -7)).isTrue();
    assertThat(discovery.isNewDiscovery(UUID.randomUUID(), 3, -7)).isTrue();
  }

  @Test
  void chunksInOneRegionShareOneEntry() {
    ChunkDiscovery discovery = new ChunkDiscovery();
    for (int x = 0; x < 32; x++) {
      for (int z = 0; z < 32; z++) {
        assertThat(discovery.isNewDiscovery(WORLD, x, z)).isTrue();
      }
    }
    assertThat(discovery.regionCount()).isEqualTo(1);
  }

  @Test
  void theOldestRegionsAreDroppedPastTheCap() {
    ChunkDiscovery discovery = new ChunkDiscovery();
    for (int region = 0; region <= ChunkDiscovery.REGION_CAP; region++) {
      discovery.isNewDiscovery(WORLD, region * 32, 0);
    }
    assertThat(discovery.regionCount()).isEqualTo(ChunkDiscovery.REGION_CAP);
    assertThat(discovery.isNewDiscovery(WORLD, 0, 0)).isTrue();
  }
}
