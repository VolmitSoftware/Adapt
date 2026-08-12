package art.arcane.adapt.util.common.scheduling;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class RegionAreaOwnershipTest {
  @Test
  void areaOwnershipChecksEveryChunkTouchedByTheHorizontalFootprint() {
    World world = mock(World.class);
    Location center = new Location(world, 15.5D, 64.0D, 15.5D);

    try (MockedStatic<FoliaScheduler> scheduling = mockStatic(FoliaScheduler.class)) {
      scheduling.when(() -> FoliaScheduler.isOwnedByCurrentRegion(same(world), anyInt(), anyInt()))
          .thenReturn(true);

      assertThat(J.isOwnedByCurrentRegion(center, 1.0D, 1.0D)).isTrue();

      scheduling.verify(() -> FoliaScheduler.isOwnedByCurrentRegion(world, 0, 0));
      scheduling.verify(() -> FoliaScheduler.isOwnedByCurrentRegion(world, 0, 1));
      scheduling.verify(() -> FoliaScheduler.isOwnedByCurrentRegion(world, 1, 0));
      scheduling.verify(() -> FoliaScheduler.isOwnedByCurrentRegion(world, 1, 1));
    }
  }

  @Test
  void areaOwnershipFailsClosedForInvalidBounds() {
    World world = mock(World.class);
    Location center = new Location(world, 0.0D, 64.0D, 0.0D);

    assertThat(J.isOwnedByCurrentRegion(center, Double.NaN, 1.0D)).isFalse();
    assertThat(J.isOwnedByCurrentRegion(center, -1.0D, 1.0D)).isFalse();
    assertThat(J.isOwnedByCurrentRegion(new Location(null, 0.0D, 64.0D, 0.0D), 1.0D, 1.0D)).isFalse();
  }
}
