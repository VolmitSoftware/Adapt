package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RiftPearlThrowIntegrityTest {
  private static final Id REBOUND_LEVEL = Id.adapt("rift_pearl_rebound_level");
  private static final Id REBOUNDED = Id.adapt("rift_pearl_rebounded");
  private static final Id TAGLOCK_TARGET = Id.adapt(RiftEnderTaglock.TARGET_KEY_NAME);

  @Test
  void pearlWithoutAnyStoredKeysIsUnclaimed() {
    assertThat(RiftPearls.isUnclaimedPearl(pearlWithKeys())).isTrue();
  }

  @Test
  void pearlCarryingOnlyItsOwnKeysStaysUnclaimed() {
    ProjectileRef pearl = pearlWithKeys(REBOUND_LEVEL, REBOUNDED);

    assertThat(RiftPearls.isUnclaimedPearl(pearl, REBOUND_LEVEL, REBOUNDED)).isTrue();
  }

  @Test
  void pearlClaimedByAnotherAdaptationIsNotUnclaimed() {
    ProjectileRef pearl = pearlWithKeys(REBOUND_LEVEL, TAGLOCK_TARGET);

    assertThat(RiftPearls.isUnclaimedPearl(pearl, REBOUND_LEVEL, REBOUNDED)).isFalse();
  }

  @Test
  void missingPearlAndMissingContainerAreNeverUnclaimed() {
    assertThat(RiftPearls.isUnclaimedPearl(null)).isFalse();
    assertThat(RiftPearls.isUnclaimedContainer(null)).isFalse();
  }

  @Test
  void reboundOnlyBouncesUnclaimedPearlsThatHaveNotBouncedYet() {
    assertThat(RiftPearlRebound.shouldRebound(2, false, true)).isTrue();
    assertThat(RiftPearlRebound.shouldRebound(null, false, true)).isFalse();
    assertThat(RiftPearlRebound.shouldRebound(0, false, true)).isFalse();
    assertThat(RiftPearlRebound.shouldRebound(2, true, true)).isFalse();
  }

  @Test
  void reboundRefusesPearlsAnotherAdaptationClaimedAfterLaunch() {
    assertThat(RiftPearlRebound.shouldRebound(2, false, false)).isFalse();
  }

  @Test
  void taglockSuppressesTheThrowerTeleportOnEveryImpactThatVanillaStillResolves() {
    assertThat(RiftEnderTaglock.vanillaPearlTeleportStillRuns(false, true)).isTrue();
    assertThat(RiftEnderTaglock.vanillaPearlTeleportStillRuns(false, false)).isTrue();
    assertThat(RiftEnderTaglock.vanillaPearlTeleportStillRuns(true, true)).isTrue();
    assertThat(RiftEnderTaglock.vanillaPearlTeleportStillRuns(true, false)).isFalse();
  }

  @Test
  void taglockSuppressionOnlyAppliesWhileItIsArmed() {
    assertThat(RiftEnderTaglock.shouldSuppressPearlTeleport(1_000L, 999L)).isTrue();
    assertThat(RiftEnderTaglock.shouldSuppressPearlTeleport(1_000L, 1_000L)).isFalse();
    assertThat(RiftEnderTaglock.shouldSuppressPearlTeleport(1_000L, 1_001L)).isFalse();
    assertThat(RiftEnderTaglock.shouldSuppressPearlTeleport(null, 0L)).isFalse();
  }

  private ProjectileRef pearlWithKeys(Id... keys) {
    ProjectileRef pearl = mock(ProjectileRef.class);
    DataHolder data = mock(DataHolder.class);
    when(pearl.data()).thenReturn(data);
    when(data.keys()).thenReturn(Set.of(keys));
    return pearl;
  }
}
