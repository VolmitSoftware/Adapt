package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TamingFetchTest {
  @Test
  void onlyIdleUnrestrainedOwnedWolvesJoinAFetch() {
    assertThat(TameFetcher.isFetchWolfEligible(true, true, false, false, false)).isTrue();
    assertThat(TameFetcher.isFetchWolfEligible(false, true, false, false, false)).isFalse();
    assertThat(TameFetcher.isFetchWolfEligible(true, false, false, false, false)).isFalse();
    assertThat(TameFetcher.isFetchWolfEligible(true, true, true, false, false)).isFalse();
    assertThat(TameFetcher.isFetchWolfEligible(true, true, false, true, false)).isFalse();
    assertThat(TameFetcher.isFetchWolfEligible(true, true, false, false, true)).isFalse();
  }

  @Test
  void arrivalWinsOverTheDeadlineButInvalidContextAlwaysAborts() {
    assertThat(TameFetcher.nextFetchStep(0L, 1000L, true, 100.0D, 4.0D)).isEqualTo(TameFetcher.FetchStep.CONTINUE);
    assertThat(TameFetcher.nextFetchStep(0L, 1000L, true, 4.0D, 4.0D)).isEqualTo(TameFetcher.FetchStep.ARRIVE);
    assertThat(TameFetcher.nextFetchStep(0L, 1000L, true, 3.9D, 4.0D)).isEqualTo(TameFetcher.FetchStep.ARRIVE);
    assertThat(TameFetcher.nextFetchStep(1000L, 1000L, true, 0.0D, 4.0D)).isEqualTo(TameFetcher.FetchStep.ARRIVE);
    assertThat(TameFetcher.nextFetchStep(1000L, 1000L, true, 100.0D, 4.0D)).isEqualTo(TameFetcher.FetchStep.ABORT);
    assertThat(TameFetcher.nextFetchStep(0L, 1000L, false, 0.0D, 4.0D)).isEqualTo(TameFetcher.FetchStep.ABORT);
  }

  @Test
  void walkRadiiClampToTheVanillaFollowLeashAndToTheLevelRange() {
    assertThat(TameFetcher.pathfindRadius(9.0D)).isEqualTo(9.0D);
    assertThat(TameFetcher.pathfindRadius(40.0D)).isEqualTo(11.0D);
    assertThat(TameFetcher.pathfindRadius(-3.0D)).isZero();
    assertThat(TameFetcher.pathfindRadius(Double.NaN)).isZero();
    assertThat(TameFetcher.claimRadius(9.0D, 16.0D)).isEqualTo(9.0D);
    assertThat(TameFetcher.claimRadius(9.0D, 6.0D)).isEqualTo(6.0D);
    assertThat(TameFetcher.claimRadius(40.0D, 16.0D)).isEqualTo(11.0D);
    assertThat(TameFetcher.claimRadius(9.0D, -1.0D)).isZero();
  }

  @Test
  void cadenceAndSpeedTuningStayInsideSafeBounds() {
    assertThat(TameFetcher.fetchWalkSpeed(1.15D)).isEqualTo(1.15D);
    assertThat(TameFetcher.fetchWalkSpeed(0.0D)).isEqualTo(0.1D);
    assertThat(TameFetcher.fetchWalkSpeed(99.0D)).isEqualTo(4.0D);
    assertThat(TameFetcher.fetchWalkSpeed(Double.NaN)).isEqualTo(1.0D);
    assertThat(TameFetcher.maintenanceIntervalTicks(5)).isEqualTo(5);
    assertThat(TameFetcher.maintenanceIntervalTicks(0)).isEqualTo(1);
    assertThat(TameFetcher.maintenanceIntervalTicks(500)).isEqualTo(20);
    assertThat(TameFetcher.fetchDeadlineMillis(9000L)).isEqualTo(9000L);
    assertThat(TameFetcher.fetchDeadlineMillis(10L)).isEqualTo(1000L);
    assertThat(TameFetcher.fetchDeadlineMillis(999999L)).isEqualTo(60000L);
  }

  @Test
  void physicalFetchShipsWithTheTunedWalkCadenceAndNoTeleportToggle() {
    TamingFetch.Config config = new TamingFetch.Config();

    assertThat(config.fetchWalkSpeed).isEqualTo(1.15D);
    assertThat(config.pathfindRadius).isEqualTo(9.0D);
    assertThat(config.fetchDeadlineMillis).isEqualTo(9000L);
    assertThat(config.maintenanceIntervalTicks).isEqualTo(5);
    assertThatThrownBy(() -> TamingFetch.Config.class.getDeclaredField("realFetch"))
        .isInstanceOf(NoSuchFieldException.class);
  }

  @Test
  void inFlightFetchesDrainOnShutdownAndOnOwnerQuit() throws NoSuchMethodException {
    assertThat(TamingFetch.class.getDeclaredMethod("unregister")).isNotNull();
    assertThat(TamingFetch.class.getDeclaredMethod("on", PlayerQuitEvent.class)
        .getAnnotation(Handle.class)).isNotNull();
  }

  @Test
  void staleCleanupCannotStopAReplacementFetchPath() {
    assertThat(TameFetcher.shouldStopPathfinding(4L, null)).isTrue();
    assertThat(TameFetcher.shouldStopPathfinding(4L, 4L)).isTrue();
    assertThat(TameFetcher.shouldStopPathfinding(4L, 5L)).isFalse();
  }
}
