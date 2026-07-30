package art.arcane.adapt.content.adaptation.ranged;

import org.bukkit.block.Block;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RangedWebBombJournalTest {
  private static final Path SOURCE =
      Path.of("src/main/java/art/arcane/adapt/content/adaptation/ranged/RangedWebBomb.java");

  @Test
  void blockCoordinatesRoundTripAcrossTheWorldHeightRange() {
    long encoded = RangedWebBomb.encodeBlock(15, 319, 0, -64);

    assertThat(RangedWebBomb.decodeX(encoded)).isEqualTo(15);
    assertThat(RangedWebBomb.decodeZ(encoded)).isZero();
    assertThat(RangedWebBomb.decodeY(encoded, -64)).isEqualTo(319);
  }

  @Test
  void journalUpsertRenewsWithoutDuplicatingAndHonorsItsCapacity() {
    long[] created = RangedWebBomb.upsertJournalEntry(null, 4L, 100L, 1);
    long[] renewed = RangedWebBomb.upsertJournalEntry(created, 4L, 200L, 1);

    assertThat(renewed).containsExactly(4L, 200L);
    assertThat(RangedWebBomb.journalExpiry(renewed, 4L)).isEqualTo(200L);
    assertThat(RangedWebBomb.upsertJournalEntry(renewed, 5L, 300L, 1)).isNull();
  }

  @Test
  void staleExpiryCannotRemoveANewerWebAtTheSamePosition() {
    long[] journal = {9L, 500L};

    assertThat(RangedWebBomb.removeJournalEntry(journal, 9L, 400L)).containsExactly(9L, 500L);
    assertThat(RangedWebBomb.removeJournalEntry(journal, 9L, 500L)).isEmpty();
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

  @Test
  void shooterStateIsAuthorizedBeforePlayerFreeRegionPlacement() throws IOException {
    String source = Files.readString(SOURCE);
    int placementStart = source.indexOf("private void scheduleAuthorizedWeb");
    int placementEnd = source.indexOf("private void scheduleWebRemoval", placementStart);
    String placementMethod = source.substring(placementStart, placementEnd);

    assertThat(source).contains("J.runEntity(p, () -> authorizeImpact(p, impact))");
    assertThat(placementMethod)
        .contains("if (!isRuntimeRegistered())")
        .doesNotContain("Player", "canBlockPlace", "getActiveLevel", "isOnline");
  }
}
