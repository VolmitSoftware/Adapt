package art.arcane.adapt.content.adaptation.sword.effects;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DamagingBleedEffectAttributionTest {
  @Test
  void damagingBleedRetainsTheCaster() {
    FakeWorld world = new FakeWorld("world");
    FakePlayer caster = new FakePlayer("Caster", new Loc(world, 0, 64, 0));
    FakeLiving target = new FakeLiving(Id.minecraft("zombie"), new Loc(world, 2, 64, 0));
    DamagingBleedEffect.DamageContext context = new DamagingBleedEffect.DamageContext(1.25D, target, caster);
    DamagingBleedEffect effect = new DamagingBleedEffect(context);

    effect.applyDamage();

    assertThat(target.damageTaken()).isEqualTo(1.25D);
    assertThat(target.lastDamageType()).isEqualTo(Id.minecraft("magic"));
    assertThat(target.lastDamageSource()).isSameAs(caster);
  }
}
