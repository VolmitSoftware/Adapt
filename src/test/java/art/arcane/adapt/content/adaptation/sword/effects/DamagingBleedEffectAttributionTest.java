package art.arcane.adapt.content.adaptation.sword.effects;

import art.arcane.adapt.api.adaptation.DamageAttributionTestSupport;
import de.slikey.effectlib.EffectManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import static org.mockito.Mockito.mock;

class DamagingBleedEffectAttributionTest {
  @BeforeAll
  static void initializeDamageTypes() {
    DamageAttributionTestSupport.initializeDamageTypes();
  }
  @Test
  void damagingBleedRetainsTheCaster() {
    EffectManager effectManager = mock(EffectManager.class);
    Player caster = mock(Player.class);
    LivingEntity target = mock(LivingEntity.class);
    DamagingBleedEffect.DamageContext context = new DamagingBleedEffect.DamageContext(1.25D, target, caster);
    DamagingBleedEffect effect = new DamagingBleedEffect(effectManager, context);

    DamageAttributionTestSupport.assertMagicDamage(
        new DamageAttributionTestSupport.DamageProbe(target, caster, 1.25D), effect::applyDamage);
  }
}
