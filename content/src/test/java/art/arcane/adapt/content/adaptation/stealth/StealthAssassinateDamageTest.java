package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakeWorld;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StealthAssassinateDamageTest {
  @Test
  void executionBypassesArmorResistanceAndAbsorptionWithoutTouchingUnsupportedModifiers() {
    FakeWorld world = new FakeWorld("world");
    FakeLiving attacker = new FakeLiving(Id.minecraft("player"), new Loc(world, 0, 64, 0));
    FakeLiving target = new FakeLiving(Id.minecraft("zombie"), new Loc(world, 1, 64, 0));
    EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(target, attacker, attacker, DamageCause.ENTITY_ATTACK,
        Id.minecraft("player_attack"), 4D, false, base -> base * 0.25D, base -> base * 0.5D);

    StealthExecution.applyExecutionDamage(event, 20D);

    assertThat(event.damage()).isEqualTo(20D);
    assertThat(event.ignoreReductions()).isTrue();
    assertThat(event.finalDamage()).isEqualTo(20D);
    assertThat(event.blockedDamage()).isZero();
  }
}
