package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.api.adaptation.RunsWithoutLearnedAdaptation;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiscoveryScanScalingTest {
  @Test
  void sixthSenseMovementCleanupRunsAfterUnlearning() throws ReflectiveOperationException {
    Method cleanup = DiscoverySixthSense.class.getDeclaredMethod("onMove", PlayerMoveEvent.class);
    EventHandler handler = cleanup.getAnnotation(EventHandler.class);

    assertThat(handler).isNotNull();
    assertThat(handler.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(handler.ignoreCancelled()).isTrue();
    assertThat(cleanup.isAnnotationPresent(RunsWithoutLearnedAdaptation.class)).isTrue();
  }

  @Test
  void keenEyeRangeAndGlimmerHoldFloorsAndScaleUp() {
    assertThat(DiscoveryKeenEye.range(0.0D, 10D, 14D)).isEqualTo(10D);
    assertThat(DiscoveryKeenEye.range(1.0D, 10D, 14D)).isEqualTo(24D);
    assertThat(DiscoveryKeenEye.glimmerDurationTicks(0.0D, 12D, 28D)).isEqualTo(12);
    assertThat(DiscoveryKeenEye.glimmerDurationTicks(1.0D, 12D, 28D)).isEqualTo(40);
    assertThat(DiscoveryKeenEye.range(0.5D, -100D, 0D)).isEqualTo(4D);
  }

  @Test
  void sixthSenseRangeScalesAndStaysAboveFloor() {
    assertThat(DiscoverySixthSense.detectionRange(0.0D, 48D, 112D)).isEqualTo(48D);
    assertThat(DiscoverySixthSense.detectionRange(1.0D, 48D, 112D)).isEqualTo(160D);
    assertThat(DiscoverySixthSense.detectionRange(0.0D, 4D, 0D)).isEqualTo(16D);
    assertThat(DiscoverySixthSense.detectionRange(1.0D, 500D, 500D, 500D)).isEqualTo(500D);
    assertThat(DiscoverySixthSense.detectionRange(1.0D, 500D, 500D, 2_000D)).isEqualTo(500D);
    assertThat(DiscoverySixthSense.detectionRange(1.0D, 500D, 0D, 240D)).isEqualTo(240D);
    assertThat(DiscoverySixthSense.detectionRange(Double.NaN, 500D, 0D, Double.NaN)).isEqualTo(500D);
    assertThat(DiscoverySixthSense.detectionRange(0.0D, 48D, 452D, 500D)).isEqualTo(48D);
    assertThat(DiscoverySixthSense.detectionRange(1.0D, 48D, 452D, 500D)).isEqualTo(500D);
  }

  @Test
  void sixthSenseTargetLifetimeAlwaysCoversAFullBoundedScanCycle() {
    assertThat(DiscoverySixthSense.pulseIntervalMillis(-1L)).isEqualTo(2_000L);
    assertThat(DiscoverySixthSense.pulseIntervalMillis(4_000L)).isEqualTo(4_000L);
    assertThat(DiscoverySixthSense.pulseIntervalMillis(Long.MAX_VALUE)).isEqualTo(60_000L);
    assertThat(DiscoverySixthSense.targetTtlMillis(4_000L, 16)).isEqualTo(72_000L);
    assertThat(DiscoverySixthSense.requiresMaintenance(false, true)).isTrue();
    assertThat(DiscoverySixthSense.requiresMaintenance(false, false)).isFalse();
  }

  @Test
  void sixthSenseCompassDirectionsUseEightCardinalSectors() {
    assertThat(DiscoverySixthSense.compassDirection(0D, -1D)).isEqualTo("N");
    assertThat(DiscoverySixthSense.compassDirection(1D, -1D)).isEqualTo("NE");
    assertThat(DiscoverySixthSense.compassDirection(1D, 0D)).isEqualTo("E");
    assertThat(DiscoverySixthSense.compassDirection(1D, 1D)).isEqualTo("SE");
    assertThat(DiscoverySixthSense.compassDirection(0D, 1D)).isEqualTo("S");
    assertThat(DiscoverySixthSense.compassDirection(-1D, 1D)).isEqualTo("SW");
    assertThat(DiscoverySixthSense.compassDirection(-1D, 0D)).isEqualTo("W");
    assertThat(DiscoverySixthSense.compassDirection(-1D, -1D)).isEqualTo("NW");
    assertThat(DiscoverySixthSense.compassDirection(0D, 0D)).isEqualTo("•");
  }

  @Test
  void sixthSenseKeepsOnlyTheNearestCurrentWorldCandidate() {
    World world = mock(World.class);
    UUID worldId = UUID.randomUUID();
    when(world.getUID()).thenReturn(worldId);
    Location origin = new Location(world, 0D, 64D, 0D);
    DiscoverySixthSense.StructureTarget current =
        new DiscoverySixthSense.StructureTarget(worldId, 100D, 0D, "Village Plains", "⌂", 1L);
    DiscoverySixthSense.StructureTarget farther =
        new DiscoverySixthSense.StructureTarget(worldId, 150D, 0D, "Mineshaft", "◇", 2L);
    DiscoverySixthSense.StructureTarget nearer =
        new DiscoverySixthSense.StructureTarget(worldId, 50D, 0D, "Shipwreck", "⚓", 3L);

    assertThat(DiscoverySixthSense.shouldReplaceTarget(null, current, origin)).isTrue();
    assertThat(DiscoverySixthSense.shouldReplaceTarget(current, farther, origin)).isFalse();
    assertThat(DiscoverySixthSense.shouldReplaceTarget(current, nearer, origin)).isTrue();
    assertThat(current.distanceSquared(origin)).isEqualTo(10_000D);
    assertThat(current.sameTarget(new DiscoverySixthSense.StructureTarget(
        worldId,
        100D,
        0D,
        "Village Plains",
        "⌂",
        99L
    ))).isTrue();
  }

  @Test
  void sixthSenseCursorCyclesPerPlayer() {
    assertThat(DiscoverySixthSense.advanceTypeCursor(0, 16)).isEqualTo(1);
    assertThat(DiscoverySixthSense.advanceTypeCursor(15, 16)).isZero();
    assertThat(DiscoverySixthSense.advanceTypeCursor(-1, 16)).isZero();
    assertThat(DiscoverySixthSense.advanceTypeCursor(4, 0)).isZero();
  }

  @Test
  void trailblazerSpeedDurationScalesAndKeepsMinimum() {
    assertThat(DiscoveryTrailblazer.speedDurationTicks(1.0D, 80D, 120D)).isEqualTo(200);
    assertThat(DiscoveryTrailblazer.speedDurationTicks(0.0D, 5D, 0D)).isEqualTo(20);
    assertThat(DiscoveryTrailblazer.firstVisitXp(0.2D, 40D, 160D)).isEqualTo(72D);
  }

  @Test
  void relicAppraiserXpScalesWithLevel() {
    assertThat(DiscoveryRelicAppraiser.appraiseXp(0.0D, 60D, 180D)).isEqualTo(60D);
    assertThat(DiscoveryRelicAppraiser.appraiseXp(1.0D, 60D, 180D)).isEqualTo(240D);
  }
}
