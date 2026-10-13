package art.arcane.adapt.content.adaptation.rift;

import org.junit.jupiter.api.Test;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import java.util.EnumMap;
import java.util.Map;
import com.google.common.base.Function;
import org.bukkit.damage.DamageSource;
import static org.mockito.Mockito.mock;

import static org.assertj.core.api.Assertions.assertThat;

class RiftVoidSkinEscapeTest {
  @Test
  void finalDamageAlreadyAccountsForAbsorptionBeforeTheLethalCheck() {
    EntityDamageEvent damage = damageWithAbsorption(18D, 8D);
    assertThat(damage.getFinalDamage()).isEqualTo(10D);
    assertThat(RiftVoidSkin.isLethalDamage(6D, damage.getFinalDamage())).isTrue();
  }

  @Test
  void aHitAbsorptionSoaksIsNotTreatedAsLethal() {
    EntityDamageEvent damage = damageWithAbsorption(10D, 8D);
    assertThat(damage.getFinalDamage()).isEqualTo(2D);
    assertThat(RiftVoidSkin.isLethalDamage(6D, damage.getFinalDamage())).isFalse();
  }

  private static EntityDamageEvent damageWithAbsorption(double damage, double absorption) {
    Map<EntityDamageEvent.DamageModifier, Double> modifiers = new EnumMap<>(EntityDamageEvent.DamageModifier.class);
    modifiers.put(EntityDamageEvent.DamageModifier.BASE, damage);
    modifiers.put(EntityDamageEvent.DamageModifier.ABSORPTION, -absorption);
    Map<EntityDamageEvent.DamageModifier, Function<? super Double, Double>> functions = new EnumMap<>(EntityDamageEvent.DamageModifier.class);
    functions.put(EntityDamageEvent.DamageModifier.BASE, amount -> amount);
    functions.put(EntityDamageEvent.DamageModifier.ABSORPTION, amount -> -Math.min(amount, absorption));
    return new EntityDamageEvent(mock(Player.class), EntityDamageEvent.DamageCause.CUSTOM, mock(DamageSource.class), modifiers, functions);
  }
}
