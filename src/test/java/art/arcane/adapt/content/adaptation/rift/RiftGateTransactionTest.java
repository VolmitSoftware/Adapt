package art.arcane.adapt.content.adaptation.rift;

import org.bukkit.potion.PotionEffect;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RiftGateTransactionTest {
  @Test
  void teleportCompletesOnlyAfterAConfirmedOnlineSuccess() {
    RuntimeException failure = new RuntimeException("failed");

    assertThat(RiftGate.successfulTeleport(true, null, true)).isTrue();
    assertThat(RiftGate.successfulTeleport(false, null, true)).isFalse();
    assertThat(RiftGate.successfulTeleport(null, null, true)).isFalse();
    assertThat(RiftGate.successfulTeleport(true, failure, true)).isFalse();
    assertThat(RiftGate.successfulTeleport(true, null, false)).isFalse();
  }

  @Test
  void deferredProviderCostMustSettleWhenItReplacesTheDefaultEye() {
    assertThat(RiftGate.acceptSettlement(true, false, false)).isTrue();
    assertThat(RiftGate.acceptSettlement(false, false, false)).isTrue();
    assertThat(RiftGate.acceptSettlement(false, true, true)).isTrue();
    assertThat(RiftGate.acceptSettlement(false, true, false)).isFalse();
  }

  @Test
  void defaultTransformReplacesAConsumedHandWhileProviderTransformAddsOutput() {
    assertThat(RiftGate.shouldReplaceTransformedEye(true, true)).isTrue();
    assertThat(RiftGate.shouldReplaceTransformedEye(true, false)).isFalse();
    assertThat(RiftGate.shouldReplaceTransformedEye(false, true)).isFalse();
    assertThat(RiftAccess.shouldReplaceBoundPearl(true, true)).isTrue();
    assertThat(RiftAccess.shouldReplaceBoundPearl(false, true)).isFalse();
  }

  @Test
  void channelCleanupRecognizesOnlyItsHiddenEffectShape() {
    PotionEffect owned = effect(10, 85, true, false, false);
    PotionEffect foreignAmplifier = effect(9, 85, true, false, false);
    PotionEffect foreignParticles = effect(10, 85, true, true, false);
    PotionEffect longer = effect(10, 101, true, false, false);

    assertThat(RiftGate.isOwnedChannelEffect(owned, 10, 100)).isTrue();
    assertThat(RiftGate.isOwnedChannelEffect(foreignAmplifier, 10, 100)).isFalse();
    assertThat(RiftGate.isOwnedChannelEffect(foreignParticles, 10, 100)).isFalse();
    assertThat(RiftGate.isOwnedChannelEffect(longer, 10, 100)).isFalse();
    assertThat(RiftGate.isOwnedChannelEffect(null, 10, 100)).isFalse();
  }

  private static PotionEffect effect(int amplifier, int duration, boolean ambient,
                                     boolean particles, boolean icon) {
    PotionEffect effect = mock(PotionEffect.class);
    when(effect.getAmplifier()).thenReturn(amplifier);
    when(effect.getDuration()).thenReturn(duration);
    when(effect.isAmbient()).thenReturn(ambient);
    when(effect.hasParticles()).thenReturn(particles);
    when(effect.hasIcon()).thenReturn(icon);
    return effect;
  }
}
