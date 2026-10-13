package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.api.adaptation.DamageAttributionTestSupport;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import static org.mockito.Mockito.mock;

class SwordDelayedDamageAttributionTest {
  @BeforeAll
  static void initializeDamageTypes() {
    DamageAttributionTestSupport.initializeDamageTypes();
  }
  @Test
  void bloodyBladePulseRetainsTheCaster() {
    Player caster = mock(Player.class);
    LivingEntity target = mock(LivingEntity.class);
    SwordsBloodyBlade.BleedPulse pulse = new SwordsBloodyBlade.BleedPulse(target, caster, 0.5D, 2);

    DamageAttributionTestSupport.assertMagicDamage(
        new DamageAttributionTestSupport.DamageProbe(target, caster, 0.5D),
        () -> SwordsBloodyBlade.applyBleedDamage(pulse));
  }
}
