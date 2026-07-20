package art.arcane.adapt.util.common.misc;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ImpulseDamageAttributionTest {
  @Test
  void impulseDamageRetainsTheCaster() {
    Player caster = mock(Player.class);
    LivingEntity target = mock(LivingEntity.class);
    Impulse impulse = new Impulse(4D, caster);

    impulse.applyDamage(target, 3D);

    verify(target).damage(3D, caster);
    verify(target, never()).damage(anyDouble());
  }
}
