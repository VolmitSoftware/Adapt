package art.arcane.adapt.content.adaptation.sword.effects;

import de.slikey.effectlib.EffectManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DamagingBleedEffectAttributionTest {
  @Test
  void damagingBleedRetainsTheCaster() {
    EffectManager effectManager = mock(EffectManager.class);
    Player caster = mock(Player.class);
    LivingEntity target = mock(LivingEntity.class);
    DamagingBleedEffect.DamageContext context = new DamagingBleedEffect.DamageContext(1.25D, target, caster);
    DamagingBleedEffect effect = new DamagingBleedEffect(effectManager, context);

    effect.applyDamage();

    verify(target).damage(1.25D, caster);
    verify(target, never()).damage(anyDouble());
  }
}
