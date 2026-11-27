package art.arcane.adapt.content.skill;

import art.arcane.adapt.engine.platform.EngineLastDamage;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.content.golem.GolemAnchor;
import art.arcane.adapt.content.golem.GolemBody;
import art.arcane.adapt.content.golem.GolemCoreType;
import art.arcane.adapt.content.golem.GolemState;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityBreedEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakeWorld;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemKillCreditSkillTest extends AdaptTestBase {
  private static final FakeWorld WORLD = new FakeWorld("world");

  @Test
  void stealthSkipsKillCreditWhenABoundGolemLandsTheKillingBlow() {
    PlayerRef player = mock(PlayerRef.class);
    FakeLiving golem = living("iron_golem");
    GolemState.create(new GolemState.Seed(UUID.randomUUID(), UUID.randomUUID(), GolemBody.IRON, GolemCoreType.BLANK,
        GolemAnchor.of(UUID.randomUUID(), 0, 64, 0))).write(golem);
    SkillStealth stealth = mock(SkillStealth.class, CALLS_REAL_METHODS);

    stealth.on(death(player, golem));

    verify(player, never()).isSneaking();
  }

  @Test
  void stealthStillCreditsThePlayersOwnKillingBlow() {
    PlayerRef player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(UUID.randomUUID());
    SkillStealth stealth = mock(SkillStealth.class, CALLS_REAL_METHODS);

    stealth.on(death(player, player));

    verify(player).isSneaking();
  }

  @Test
  void hunterPaysNothingForAnAnimalARancherCulledAfterTheOwnersHit() {
    PlayerRef owner = mock(PlayerRef.class);
    SkillHunter hunter = mock(SkillHunter.class, CALLS_REAL_METHODS);

    hunter.on(culled(owner, boundIronGolem()));

    verify(hunter, never()).getConfig();
  }

  @Test
  void tamingPaysNothingForARancherCullOrARancherBreeding() {
    SkillTaming taming = mock(SkillTaming.class, CALLS_REAL_METHODS);
    EntityBreedEvent breeding = new EntityBreedEvent(living("cow"), living("cow"), living("cow"), null, null, 0);

    taming.on(culled(null, boundIronGolem()));
    taming.on(breeding);

    verify(taming, never()).getConfig();
  }

  private static FakeLiving boundIronGolem() {
    FakeLiving golem = living("iron_golem");
    GolemState.create(new GolemState.Seed(UUID.randomUUID(), UUID.randomUUID(), GolemBody.IRON, GolemCoreType.RANCHER,
        GolemAnchor.of(UUID.randomUUID(), 0, 64, 0))).write(golem);
    return golem;
  }

  private static EntityDeathEvent culled(PlayerRef killer, EntityRef golem) {
    FakeLiving victim = living("cow");
    victim.setLastDamage(new EngineLastDamage(DamageCause.ENTITY_ATTACK, golem, 4D));
    return new EntityDeathEvent(victim, killer, golem, golem, DamageCause.ENTITY_ATTACK, List.of(), 0);
  }

  private static EntityDeathEvent death(PlayerRef killer, EntityRef lastDamager) {
    FakeLiving victim = living("zombie");
    victim.setLastDamage(new EngineLastDamage(DamageCause.ENTITY_ATTACK, lastDamager, 4D));
    return new EntityDeathEvent(victim, killer, lastDamager, lastDamager, DamageCause.ENTITY_ATTACK, List.of(), 0);
  }

  private static FakeLiving living(String type) {
    return new FakeLiving(Id.minecraft(type), new Loc(WORLD, 0, 64, 0, 0F, 0F));
  }
}
