package art.arcane.adapt.content.adaptation.tragoul;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TragoulBloodPactScalingTest {
  @Test
  void speedBonusMatchesVanillaSpeedPotionParity() {
    assertThat(TragoulBloodPact.speedBonus(0)).isCloseTo(0.2D, within(1.0e-9D));
    assertThat(TragoulBloodPact.speedBonus(1)).isCloseTo(0.4D, within(1.0e-9D));
    assertThat(TragoulBloodPact.speedBonus(2)).isCloseTo(0.6D, within(1.0e-9D));
  }

  @Test
  void jumpStrengthBonusMatchesJumpBoostAmplifierParity() {
    assertThat(TragoulBloodPact.jumpStrengthBonus(0)).isCloseTo(0.1D, within(1.0e-9D));
    assertThat(TragoulBloodPact.jumpStrengthBonus(1)).isCloseTo(0.2D, within(1.0e-9D));
    assertThat(TragoulBloodPact.jumpStrengthBonus(2)).isCloseTo(0.3D, within(1.0e-9D));
  }

  @Test
  void safeFallBonusAddsOneBlockPerAmplifierTier() {
    assertThat(TragoulBloodPact.safeFallBonus(0)).isCloseTo(1.0D, within(1.0e-9D));
    assertThat(TragoulBloodPact.safeFallBonus(1)).isCloseTo(2.0D, within(1.0e-9D));
    assertThat(TragoulBloodPact.safeFallBonus(2)).isCloseTo(3.0D, within(1.0e-9D));
  }

  @Test
  void damageProcObservesSettledUncancelledDamageAtMonitor() throws ReflectiveOperationException {
    Method handler = TragoulBloodPact.class.getDeclaredMethod("on", EntityDamageEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);

    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(policy.ignoreCancelled()).isTrue();
  }

  @Test
  void deferredDamageMustStillMeetTheSettledTriggerThreshold() {
    assertThat(TragoulBloodPact.isTriggeringDamage(4D, 4D)).isTrue();
    assertThat(TragoulBloodPact.isTriggeringDamage(3.99D, 4D)).isFalse();
    assertThat(TragoulBloodPact.isTriggeringDamage(0D, 4D)).isFalse();
    assertThat(TragoulBloodPact.isTriggeringDamage(Double.NaN, 4D)).isFalse();
  }
}
