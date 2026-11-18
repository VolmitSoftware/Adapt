package art.arcane.adapt.content.skill;

import art.arcane.adapt.platform.entity.LastDamage;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityBreedEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakeWorld;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SkillTamingTest {
  private static final FakeWorld WORLD = new FakeWorld("world");

  @Test
  void tamingPaysNothingForARancherCullOrARancherBreeding() {
    SkillTaming taming = mock(SkillTaming.class, CALLS_REAL_METHODS);
    FakeLiving golem = living("iron_golem");
    golem.trait(EntityTrait.GOLEM);
    FakeLiving victim = living("cow");
    victim.trait(EntityTrait.ANIMAL);
    victim.setLastDamage(new LastDamage(DamageCause.ENTITY_ATTACK, golem, 4D));
    EntityBreedEvent breeding = new EntityBreedEvent(living("cow"), living("cow"), living("cow"), null,
        FakeItem.of("wheat"), 1);

    taming.on(new EntityDeathEvent(victim, null, golem, golem, DamageCause.ENTITY_ATTACK, List.of(), 0));
    taming.on(breeding);

    verify(taming, never()).getConfig();
  }

  private static FakeLiving living(String type) {
    return new FakeLiving(Id.minecraft(type), new Loc(WORLD, 0, 64, 0, 0F, 0F));
  }
}
