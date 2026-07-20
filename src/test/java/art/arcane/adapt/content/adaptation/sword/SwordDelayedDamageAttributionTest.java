package art.arcane.adapt.content.adaptation.sword;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SwordDelayedDamageAttributionTest {
  @Test
  void bloodyBladePulseRetainsTheCaster() {
    Player caster = mock(Player.class);
    LivingEntity target = mock(LivingEntity.class);
    SwordsBloodyBlade.BleedPulse pulse = new SwordsBloodyBlade.BleedPulse(target, caster, 0.5D, 2);

    SwordsBloodyBlade.applyBleedDamage(pulse);

    verify(target).damage(0.5D, caster);
    verify(target, never()).damage(anyDouble());
  }
}
