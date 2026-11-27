package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RiftVoidSkinEscapeTest {
  @Test
  void finalDamageAlreadyAccountsForAbsorptionBeforeTheLethalCheck() {
    EntityDamageEvent damage = damageWithAbsorption(18D, 8D);
    assertThat(damage.finalDamage()).isEqualTo(10D);
    assertThat(RiftVoidSkin.isLethalDamage(6D, damage.finalDamage())).isTrue();
  }

  @Test
  void aHitAbsorptionSoaksIsNotTreatedAsLethal() {
    EntityDamageEvent damage = damageWithAbsorption(10D, 8D);
    assertThat(damage.finalDamage()).isEqualTo(2D);
    assertThat(RiftVoidSkin.isLethalDamage(6D, damage.finalDamage())).isFalse();
  }

  private static EntityDamageEvent damageWithAbsorption(double damage, double absorption) {
    return new EntityDamageEvent(mock(PlayerRef.class), DamageCause.CUSTOM, Id.minecraft("generic"), damage,
        (double base) -> base - Math.min(base, absorption));
  }
}
