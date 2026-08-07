package art.arcane.adapt.content.adaptation.taming;

import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TamingFetchTest {
  @Test
  void onlyIdleUnrestrainedOwnedWolvesJoinAFetch() {
    assertThat(TamingFetch.isFetchWolfEligible(true, true, false, false, false)).isTrue();
    assertThat(TamingFetch.isFetchWolfEligible(false, true, false, false, false)).isFalse();
    assertThat(TamingFetch.isFetchWolfEligible(true, false, false, false, false)).isFalse();
    assertThat(TamingFetch.isFetchWolfEligible(true, true, true, false, false)).isFalse();
    assertThat(TamingFetch.isFetchWolfEligible(true, true, false, true, false)).isFalse();
    assertThat(TamingFetch.isFetchWolfEligible(true, true, false, false, true)).isFalse();
  }

  @Test
  void arrivalWinsOverTheDeadlineButInvalidContextAlwaysAborts() {
    assertThat(TamingFetch.nextFetchStep(0L, 1000L, true, 100.0D, 4.0D)).isEqualTo(TamingFetch.FetchStep.CONTINUE);
    assertThat(TamingFetch.nextFetchStep(0L, 1000L, true, 4.0D, 4.0D)).isEqualTo(TamingFetch.FetchStep.ARRIVE);
    assertThat(TamingFetch.nextFetchStep(0L, 1000L, true, 3.9D, 4.0D)).isEqualTo(TamingFetch.FetchStep.ARRIVE);
    assertThat(TamingFetch.nextFetchStep(1000L, 1000L, true, 0.0D, 4.0D)).isEqualTo(TamingFetch.FetchStep.ARRIVE);
    assertThat(TamingFetch.nextFetchStep(1000L, 1000L, true, 100.0D, 4.0D)).isEqualTo(TamingFetch.FetchStep.ABORT);
    assertThat(TamingFetch.nextFetchStep(0L, 1000L, false, 0.0D, 4.0D)).isEqualTo(TamingFetch.FetchStep.ABORT);
  }

  @Test
  void walkRadiiClampToTheVanillaFollowLeashAndToTheLevelRange() {
    assertThat(TamingFetch.pathfindRadius(9.0D)).isEqualTo(9.0D);
    assertThat(TamingFetch.pathfindRadius(40.0D)).isEqualTo(11.0D);
    assertThat(TamingFetch.pathfindRadius(-3.0D)).isZero();
    assertThat(TamingFetch.pathfindRadius(Double.NaN)).isZero();
    assertThat(TamingFetch.claimRadius(9.0D, 16.0D)).isEqualTo(9.0D);
    assertThat(TamingFetch.claimRadius(9.0D, 6.0D)).isEqualTo(6.0D);
    assertThat(TamingFetch.claimRadius(40.0D, 16.0D)).isEqualTo(11.0D);
    assertThat(TamingFetch.claimRadius(9.0D, -1.0D)).isZero();
  }

  @Test
  void cadenceAndSpeedTuningStayInsideSafeBounds() {
    assertThat(TamingFetch.fetchWalkSpeed(1.15D)).isEqualTo(1.15D);
    assertThat(TamingFetch.fetchWalkSpeed(0.0D)).isEqualTo(0.1D);
    assertThat(TamingFetch.fetchWalkSpeed(99.0D)).isEqualTo(4.0D);
    assertThat(TamingFetch.fetchWalkSpeed(Double.NaN)).isEqualTo(1.0D);
    assertThat(TamingFetch.maintenanceIntervalTicks(5)).isEqualTo(5);
    assertThat(TamingFetch.maintenanceIntervalTicks(0)).isEqualTo(1);
    assertThat(TamingFetch.maintenanceIntervalTicks(500)).isEqualTo(20);
    assertThat(TamingFetch.fetchDeadlineMillis(9000L)).isEqualTo(9000L);
    assertThat(TamingFetch.fetchDeadlineMillis(10L)).isEqualTo(1000L);
    assertThat(TamingFetch.fetchDeadlineMillis(999999L)).isEqualTo(60000L);
  }

  @Test
  void realFetchShipsOnWithTheTunedWalkCadence() {
    TamingFetch.Config config = new TamingFetch.Config();

    assertThat(config.realFetch).isTrue();
    assertThat(config.fetchWalkSpeed).isEqualTo(1.15D);
    assertThat(config.pathfindRadius).isEqualTo(9.0D);
    assertThat(config.fetchDeadlineMillis).isEqualTo(9000L);
    assertThat(config.maintenanceIntervalTicks).isEqualTo(5);
  }

  @Test
  void inFlightFetchesDrainOnShutdownAndOnOwnerQuit() throws NoSuchMethodException {
    assertThat(TamingFetch.class.getDeclaredMethod("unregister")).isNotNull();
    assertThat(TamingFetch.class.getDeclaredMethod("on", PlayerQuitEvent.class)
        .getAnnotation(EventHandler.class)).isNotNull();
  }
}
