package art.arcane.adapt.content.adaptation.ranged;

import org.bukkit.block.Block;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RangedWebBombJournalTest {
  @Test
  void blockCoordinatesRoundTripAcrossTheWorldHeightRange() {
    long encoded = RangedWebBomb.encodeBlock(15, 319, 0, -64);

    assertThat(RangedWebBomb.decodeX(encoded)).isEqualTo(15);
    assertThat(RangedWebBomb.decodeZ(encoded)).isZero();
    assertThat(RangedWebBomb.decodeY(encoded, -64)).isEqualTo(319);
  }

  @Test
  void journalUpsertRenewsWithoutDuplicatingAndHonorsItsCapacity() {
    UUID owner = UUID.randomUUID();
    long[] created = RangedWebBomb.upsertJournalEntry(null, new RangedWebBomb.WebJournalEntry(4L, 100L, owner), 1);
    long[] renewed = RangedWebBomb.upsertJournalEntry(created, new RangedWebBomb.WebJournalEntry(4L, 200L, owner), 1);

    assertThat(renewed).containsExactly(4L, 200L, owner.getMostSignificantBits(), owner.getLeastSignificantBits());
    assertThat(RangedWebBomb.journalExpiry(renewed, 4L)).isEqualTo(200L);
    assertThat(RangedWebBomb.journalOwner(renewed, 4L)).isEqualTo(owner);
    assertThat(RangedWebBomb.upsertJournalEntry(renewed, new RangedWebBomb.WebJournalEntry(5L, 300L, owner), 1)).isNull();
  }

  @Test
  void staleExpiryCannotRemoveANewerWebAtTheSamePosition() {
    long[] journal = {9L, 500L, 1L, 2L};

    assertThat(RangedWebBomb.removeJournalEntry(journal, 9L, 400L)).containsExactly(journal);
    assertThat(RangedWebBomb.removeJournalEntry(journal, 9L, 500L)).isEmpty();
  }

  @Test
  void replacingAWebUpdatesItsAuthorWithoutChangingOtherEntries() {
    UUID first = UUID.randomUUID();
    UUID replacement = UUID.randomUUID();
    long[] journal = RangedWebBomb.upsertJournalEntry(null, new RangedWebBomb.WebJournalEntry(9L, 500L, first), 2);
    journal = RangedWebBomb.upsertJournalEntry(journal, new RangedWebBomb.WebJournalEntry(10L, 600L, first), 2);
    long[] updated = RangedWebBomb.upsertJournalEntry(journal, new RangedWebBomb.WebJournalEntry(9L, 700L, replacement), 2);
    assertThat(RangedWebBomb.journalOwner(updated, 9L)).isEqualTo(replacement);
    assertThat(RangedWebBomb.journalOwner(updated, 10L)).isEqualTo(first);
    assertThat(RangedWebBomb.journalOwner(journal, 9L)).isEqualTo(first);
    assertThat(RangedWebBomb.removeJournalEntry(updated, 9L, 700L))
        .containsExactly(10L, 600L, first.getMostSignificantBits(), first.getLeastSignificantBits());
  }

  @Test
  void expiryDelayRoundsUpAndNeverSchedulesAtZeroTicks() {
    assertThat(RangedWebBomb.delayTicksUntil(1_001L, 1_000L)).isEqualTo(1);
    assertThat(RangedWebBomb.delayTicksUntil(1_051L, 1_000L)).isEqualTo(2);
    assertThat(RangedWebBomb.delayTicksUntil(999L, 1_000L)).isEqualTo(1);
  }

  @Test
  void explosionsRemoveOnlyOwnedWebsFromTheirBlockLists() {
    Block owned = mock(Block.class);
    Block ordinary = mock(Block.class);
    List<Block> blocks = new ArrayList<>(List.of(owned, ordinary));

    RangedWebBomb.protectActiveWebs(blocks, block -> block == owned);

    assertThat(blocks).containsExactly(ordinary);
  }

  @Test
  void launchTrackingObservesTheFinalUncancelledEventState() throws Exception {
    Method handler = RangedWebBomb.class.getDeclaredMethod("on", ProjectileLaunchEvent.class);
    EventHandler eventHandler = handler.getAnnotation(EventHandler.class);

    assertThat(eventHandler).isNotNull();
    assertThat(eventHandler.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(eventHandler.ignoreCancelled()).isTrue();
  }

  @Test
  void impactProducesSevenDistinctImmutablePlacementCoordinates() {
    World world = mock(World.class);

    List<RangedWebBomb.WebPlacement> placements =
        RangedWebBomb.placementCandidates(world, 10, 64, -4);

    assertThat(placements).hasSize(7).doesNotHaveDuplicates();
    assertThat(placements)
        .contains(new RangedWebBomb.WebPlacement(world, 10, 64, -4))
        .contains(new RangedWebBomb.WebPlacement(world, 10, 65, -4))
        .contains(new RangedWebBomb.WebPlacement(world, 9, 64, -4));
  }
}
