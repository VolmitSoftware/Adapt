package art.arcane.adapt.api.adaptation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VelocityBurstRuntimeTest {
  @Test
  void productionScaleHasAnExactGlobalCallbackCeiling() {
    assertThat(VelocityBurstRuntime.boundedOwnerCallbacks(-1)).isZero();
    assertThat(VelocityBurstRuntime.boundedOwnerCallbacks(40)).isEqualTo(40);
    assertThat(VelocityBurstRuntime.boundedOwnerCallbacks(1_000))
        .isEqualTo(VelocityBurstRuntime.MAX_OWNER_CALLBACKS_PER_TICK);
    assertThat(VelocityBurstRuntime.MAX_OWNER_CALLBACKS_PER_SECOND).isEqualTo(6_400);
  }

  @Test
  void elapsedCadenceScalesMovementWithoutUnboundedCatchup() {
    assertThat(VelocityBurstRuntime.elapsedTickScale(-1L)).isEqualTo(1D);
    assertThat(VelocityBurstRuntime.elapsedTickScale(25L)).isEqualTo(1D);
    assertThat(VelocityBurstRuntime.elapsedTickScale(150L)).isEqualTo(3D);
    assertThat(VelocityBurstRuntime.elapsedTickScale(200L)).isEqualTo(4D);
    assertThat(VelocityBurstRuntime.elapsedTickScale(5_000L)).isEqualTo(4D);
  }

  @Test
  void overlapExtendsOnlyAcceptedBursts() {
    VelocityBurstRuntime.StartDecision started = VelocityBurstRuntime.decideStart(
        false, 0L, 0, 1_000L, 250L, 2, false);
    VelocityBurstRuntime.StartDecision rejected = VelocityBurstRuntime.decideStart(
        true, started.expiresAt(), started.amplifier(), 1_100L, 250L, 5, false);
    VelocityBurstRuntime.StartDecision extended = VelocityBurstRuntime.decideStart(
        true, started.expiresAt(), started.amplifier(), 1_100L, 250L, 5, true);

    assertThat(started.result()).isEqualTo(VelocityBurstRuntime.StartResult.STARTED);
    assertThat(started.expiresAt()).isEqualTo(1_250L);
    assertThat(rejected.result()).isEqualTo(VelocityBurstRuntime.StartResult.REJECTED_ACTIVE);
    assertThat(rejected.expiresAt()).isEqualTo(1_250L);
    assertThat(extended.result()).isEqualTo(VelocityBurstRuntime.StartResult.EXTENDED);
    assertThat(extended.expiresAt()).isEqualTo(1_500L);
    assertThat(extended.amplifier()).isEqualTo(5);
  }

  @Test
  void expiredBurstsRestartAndExpiryArithmeticSaturates() {
    VelocityBurstRuntime.StartDecision restarted = VelocityBurstRuntime.decideStart(
        true, 1_000L, 8, 1_000L, 100L, 1, false);
    VelocityBurstRuntime.StartDecision saturated = VelocityBurstRuntime.decideStart(
        true, Long.MAX_VALUE - 20L, 1, 0L, 100L, 2, true);

    assertThat(restarted.result()).isEqualTo(VelocityBurstRuntime.StartResult.STARTED);
    assertThat(restarted.expiresAt()).isEqualTo(1_100L);
    assertThat(restarted.amplifier()).isEqualTo(1);
    assertThat(saturated.expiresAt()).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  void ownerCleanupCannotRemoveAnotherAdaptationsSession() {
    VelocityBurstRuntime.SessionLedger<Object, Object> sessions =
        new VelocityBurstRuntime.SessionLedger<>(VelocityBurstRuntime.MAX_SESSIONS_PER_PLAYER);
    Object hunter = new Object();
    Object bloodPact = new Object();
    Object hunterSession = new Object();
    Object bloodPactSession = new Object();
    Object staleHunterSession = new Object();

    assertThat(sessions.put(hunter, hunterSession)).isTrue();
    assertThat(sessions.put(bloodPact, bloodPactSession)).isTrue();
    assertThat(sessions.remove(hunter, staleHunterSession)).isFalse();
    assertThat(sessions.size()).isEqualTo(2);
    assertThat(sessions.remove(hunter, hunterSession)).isTrue();
    assertThat(sessions.get(bloodPact)).isSameAs(bloodPactSession);
    assertThat(sessions.size()).isEqualTo(1);

    sessions.clear();

    assertThat(sessions.isEmpty()).isTrue();
  }

  @Test
  void perPlayerSessionCapacityIsHardBounded() {
    VelocityBurstRuntime.SessionLedger<Object, Object> sessions =
        new VelocityBurstRuntime.SessionLedger<>(2);
    Object firstOwner = new Object();
    Object secondOwner = new Object();

    assertThat(sessions.put(firstOwner, new Object())).isTrue();
    assertThat(sessions.put(secondOwner, new Object())).isTrue();
    assertThat(sessions.put(new Object(), new Object())).isFalse();
    assertThat(sessions.size()).isEqualTo(2);
    assertThat(sessions.put(firstOwner, new Object())).isTrue();
    assertThat(sessions.size()).isEqualTo(2);
  }
}
