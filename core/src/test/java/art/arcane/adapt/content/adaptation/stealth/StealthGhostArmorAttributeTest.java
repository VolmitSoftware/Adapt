package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class StealthGhostArmorAttributeTest {
  @Test
  void nextArmorAmountAccumulatesTowardTheMaximum() {
    assertThat(StealthArmorLayer.nextArmorAmount(0D, 16D, 3D)).isEqualTo(3D);
    assertThat(StealthArmorLayer.nextArmorAmount(15D, 16D, 3D)).isEqualTo(16D);
    assertThat(StealthArmorLayer.nextArmorAmount(16D, 16D, 3D)).isEqualTo(16D);
  }

  @Test
  void nextArmorAmountClampsHostileInputs() {
    assertThat(StealthArmorLayer.nextArmorAmount(10D, -4D, 3D)).isZero();
    assertThat(StealthArmorLayer.nextArmorAmount(10D, 50D, 30D)).isEqualTo(20D);
    assertThat(StealthArmorLayer.nextArmorAmount(5D, 16D, -3D)).isEqualTo(5D);
    assertThat(StealthArmorLayer.nextArmorAmount(5D, 16D, Double.NaN)).isEqualTo(5D);
    assertThat(StealthArmorLayer.nextArmorAmount(Double.NaN, 16D, 2D)).isEqualTo(2D);
    assertThat(StealthArmorLayer.nextArmorAmount(5D, Double.POSITIVE_INFINITY, 2D)).isZero();
  }

  @Test
  void chargeConsumptionRequiresMoreThanTheMinimumStoredArmor() {
    assertThat(StealthArmorLayer.isConsumableCharge(0D)).isFalse();
    assertThat(StealthArmorLayer.isConsumableCharge(0.1D)).isFalse();
    assertThat(StealthArmorLayer.isConsumableCharge(0.1000001D)).isTrue();
    assertThat(StealthArmorLayer.isConsumableCharge(20D)).isTrue();
  }

  @Test
  void damageConsumptionKeepsTheUncancelledResolvedDamageGate() throws ReflectiveOperationException {
    Method handler = StealthGhostArmor.class.getDeclaredMethod("on", EntityDamageEvent.class);
    Handle policy = handler.getAnnotation(Handle.class);

    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(handler.isAnnotationPresent(ReceiveCancelledEvents.class)).isFalse();
  }
}
