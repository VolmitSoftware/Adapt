package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RangedWebBombJournalTest {
  @Test
  void blockCoordinatesRoundTripAcrossTheWorldHeightRange() {
    long encoded = RangedWebs.encodeBlock(15, 319, 0, -64);

    assertThat(RangedWebs.decodeX(encoded)).isEqualTo(15);
    assertThat(RangedWebs.decodeZ(encoded)).isZero();
    assertThat(RangedWebs.decodeY(encoded, -64)).isEqualTo(319);
  }

  @Test
  void journalUpsertRenewsWithoutDuplicatingAndHonorsItsCapacity() {
    long[] created = RangedWebs.upsertJournalEntry(null, 4L, 100L, 1);
    long[] renewed = RangedWebs.upsertJournalEntry(created, 4L, 200L, 1);

    assertThat(renewed).containsExactly(4L, 200L);
    assertThat(RangedWebs.journalExpiry(renewed, 4L)).isEqualTo(200L);
    assertThat(RangedWebs.upsertJournalEntry(renewed, 5L, 300L, 1)).isNull();
  }

  @Test
  void staleExpiryCannotRemoveANewerWebAtTheSamePosition() {
    long[] journal = {9L, 500L};

    assertThat(RangedWebs.removeJournalEntry(journal, 9L, 400L)).containsExactly(9L, 500L);
    assertThat(RangedWebs.removeJournalEntry(journal, 9L, 500L)).isEmpty();
  }

  @Test
  void expiryDelayRoundsUpAndNeverSchedulesAtZeroTicks() {
    assertThat(RangedWebs.delayTicksUntil(1_001L, 1_000L)).isEqualTo(1);
    assertThat(RangedWebs.delayTicksUntil(1_051L, 1_000L)).isEqualTo(2);
    assertThat(RangedWebs.delayTicksUntil(999L, 1_000L)).isEqualTo(1);
  }

  @Test
  void explosionsRemoveOnlyOwnedWebsFromTheirBlockLists() {
    BlockRef owned = mock(BlockRef.class);
    BlockRef ordinary = mock(BlockRef.class);
    List<BlockRef> blocks = new ArrayList<>(List.of(owned, ordinary));

    RangedWebs.protectActiveWebs(blocks, block -> block == owned);

    assertThat(blocks).containsExactly(ordinary);
  }

  @Test
  void launchTrackingObservesTheFinalUncancelledEventState() throws Exception {
    Method handler = RangedWebBomb.class.getDeclaredMethod("on", ProjectileLaunchEvent.class);
    Handle eventHandler = handler.getAnnotation(Handle.class);

    assertThat(eventHandler).isNotNull();
    assertThat(eventHandler.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(eventHandler.ignoreCancelled()).isTrue();
  }

  @Test
  void impactProducesSevenDistinctImmutablePlacementCoordinates() {
    WorldRef world = mock(WorldRef.class);

    List<RangedWebs.WebPlacement> placements =
        RangedWebs.placementCandidates(world, 10, 64, -4);

    assertThat(placements).hasSize(7).doesNotHaveDuplicates();
    assertThat(placements)
        .contains(new RangedWebs.WebPlacement(world, 10, 64, -4))
        .contains(new RangedWebs.WebPlacement(world, 10, 65, -4))
        .contains(new RangedWebs.WebPlacement(world, 9, 64, -4));
  }
}
