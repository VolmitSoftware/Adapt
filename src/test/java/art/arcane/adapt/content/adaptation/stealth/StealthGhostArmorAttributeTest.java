package art.arcane.adapt.content.adaptation.stealth;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class StealthGhostArmorAttributeTest {
  @Test
  void nextArmorAmountAccumulatesTowardTheMaximum() {
    assertThat(StealthGhostArmor.nextArmorAmount(0D, 16D, 3D)).isEqualTo(3D);
    assertThat(StealthGhostArmor.nextArmorAmount(15D, 16D, 3D)).isEqualTo(16D);
    assertThat(StealthGhostArmor.nextArmorAmount(16D, 16D, 3D)).isEqualTo(16D);
  }

  @Test
  void nextArmorAmountClampsHostileInputs() {
    assertThat(StealthGhostArmor.nextArmorAmount(10D, -4D, 3D)).isZero();
    assertThat(StealthGhostArmor.nextArmorAmount(10D, 50D, 30D)).isEqualTo(20D);
    assertThat(StealthGhostArmor.nextArmorAmount(5D, 16D, -3D)).isEqualTo(5D);
    assertThat(StealthGhostArmor.nextArmorAmount(5D, 16D, Double.NaN)).isEqualTo(5D);
    assertThat(StealthGhostArmor.nextArmorAmount(Double.NaN, 16D, 2D)).isEqualTo(2D);
    assertThat(StealthGhostArmor.nextArmorAmount(5D, Double.POSITIVE_INFINITY, 2D)).isZero();
  }

  @Test
  void chargeConsumptionRequiresMoreThanTheMinimumStoredArmor() {
    assertThat(StealthGhostArmor.isConsumableCharge(0D)).isFalse();
    assertThat(StealthGhostArmor.isConsumableCharge(0.1D)).isFalse();
    assertThat(StealthGhostArmor.isConsumableCharge(0.1000001D)).isTrue();
    assertThat(StealthGhostArmor.isConsumableCharge(20D)).isTrue();
  }

  @Test
  void damageConsumptionKeepsTheUncancelledResolvedDamageGate() throws ReflectiveOperationException {
    Method handler = StealthGhostArmor.class.getDeclaredMethod("on", EntityDamageEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);

    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(policy.ignoreCancelled()).isTrue();
  }
}
