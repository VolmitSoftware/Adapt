package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SwordDelayedDamageAttributionTest {
  @Test
  void bloodyBladePulseRetainsTheCaster() {
    FakeWorld world = new FakeWorld("world");
    FakePlayer caster = new FakePlayer("Caster", new Loc(world, 0, 64, 0));
    FakeLiving target = new FakeLiving(Id.minecraft("zombie"), new Loc(world, 2, 64, 0));
    SwordsBloodyBlade.BleedPulse pulse = new SwordsBloodyBlade.BleedPulse(target, caster, 0.5D, 2);

    SwordsBloodyBlade.applyBleedDamage(pulse);

    assertThat(target.damageTaken()).isEqualTo(0.5D);
    assertThat(target.lastDamageType()).isEqualTo(Id.minecraft("magic"));
    assertThat(target.lastDamageSource()).isSameAs(caster);
  }
}
