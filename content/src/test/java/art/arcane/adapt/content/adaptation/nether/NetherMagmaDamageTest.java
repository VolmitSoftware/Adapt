package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NetherMagmaDamageTest {
  private static final Id HOT_FLOOR = Id.minecraft("hot_floor");
  private static final Id CACTUS = Id.minecraft("cactus");
  private static final Id SWEET_BERRY_BUSH = Id.minecraft("sweet_berry_bush");
  private static final Id IN_FIRE = Id.minecraft("in_fire");

  @Test
  void contactFromMagmaTriggersBothNetherAdaptations() {
    EntityDamageEvent event = damage(DamageCause.CONTACT, HOT_FLOOR);

    assertThat(NetherBlazeLeech.isFireDamage(event)).isTrue();
    assertThat(NetherAshwalker.isMagmaDamage(event)).isTrue();
  }

  @Test
  void otherContactDamageCannotTriggerMagmaProtectionOrLeech() {
    for (Id type : new Id[]{CACTUS, SWEET_BERRY_BUSH}) {
      EntityDamageEvent event = damage(DamageCause.CONTACT, type);

      assertThat(NetherBlazeLeech.isFireDamage(event)).isFalse();
      assertThat(NetherAshwalker.isMagmaDamage(event)).isFalse();
    }
  }

  @Test
  void ordinaryFireStillTriggersLeechWithoutMagmaImmunity() {
    EntityDamageEvent event = damage(DamageCause.FIRE, IN_FIRE);

    assertThat(NetherBlazeLeech.isFireDamage(event)).isTrue();
    assertThat(NetherAshwalker.isMagmaDamage(event)).isFalse();
  }

  private static EntityDamageEvent damage(DamageCause cause, Id type) {
    FakePlayer victim = new FakePlayer("Victim", new Loc(new FakeWorld("world"), 0D, 64D, 0D));
    return new EntityDamageEvent(victim, cause, type, 1D, FinalDamage.IDENTITY);
  }
}
