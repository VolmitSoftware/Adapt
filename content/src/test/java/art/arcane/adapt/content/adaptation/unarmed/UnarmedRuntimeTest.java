package art.arcane.adapt.content.adaptation.unarmed;

import art.arcane.adapt.engine.platform.EngineLastDamage;
import art.arcane.adapt.AdaptCore;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.content.golem.GolemState;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillUnarmed;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnarmedRuntimeTest {
  private static final Id PLAYER_ATTACK = Id.minecraft("player_attack");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Brawler", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillUnarmed.class));
    profile = adapt.join(player);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertTrue(adapt.handlerFailures().isEmpty(), () -> String.valueOf(adapt.handlerFailures()));
  }

  @Test
  void bareHandedKillCountsAsUnarmedKill() {
    adapt.post(death(zombie()));

    assertEquals(1D, profile.getData().getStat("unarmed.kills"));
  }

  @Test
  void weaponKillIsNotAnUnarmedKill() {
    player.holding(FakeItem.tool("iron_sword", 0));

    adapt.post(death(zombie()));

    assertEquals(0D, profile.getData().getStat("unarmed.kills"));
  }

  @Test
  void golemKillPaysNoUnarmedKill() {
    FakeLiving victim = zombie();
    victim.setLastDamage(new EngineLastDamage(DamageCause.ENTITY_ATTACK, boundGolem(), 4D));

    adapt.post(death(victim));

    assertEquals(0D, profile.getData().getStat("unarmed.kills"));
  }

  @Test
  void sprintPunchKnockoutCountsOnePunchKill() {
    learn(UnarmedSuckerPunch.class, 1);
    player.setSprinting(true);
    FakeLiving zombie = zombie();

    EntityDamageByEntityEvent hit = adapt.post(punch(zombie, 30D));
    zombie.setLastDamage(new EngineLastDamage(DamageCause.ENTITY_ATTACK, player, hit.finalDamage()));
    adapt.post(death(zombie));

    assertTrue(hit.damage() > 30D);
    assertEquals(1D, profile.getData().getStat("unarmed.sucker-punch.sucker-punches"));
    assertEquals(1D, profile.getData().getStat("unarmed.sucker-punch.one-punch-kills"));
  }

  @Test
  void weakPunchKillIsNoKnockout() {
    learn(UnarmedSuckerPunch.class, 1);
    player.setSprinting(true);
    FakeLiving zombie = zombie();

    EntityDamageByEntityEvent hit = adapt.post(punch(zombie, 1D));
    zombie.setLastDamage(new EngineLastDamage(DamageCause.ENTITY_ATTACK, player, hit.finalDamage()));
    adapt.post(death(zombie));

    assertEquals(0D, profile.getData().getStat("unarmed.sucker-punch.one-punch-kills"));
  }

  @Test
  void knockoutNeedsThePunchToBeTheLastDamage() {
    learn(UnarmedSuckerPunch.class, 1);
    player.setSprinting(true);
    FakeLiving zombie = zombie();

    adapt.post(punch(zombie, 30D));
    zombie.setLastDamage(new EngineLastDamage(DamageCause.FIRE_TICK, null, 1D));
    adapt.post(death(zombie));

    assertEquals(0D, profile.getData().getStat("unarmed.sucker-punch.one-punch-kills"));
  }

  @Test
  void powerKillStatFollowsTheLastDamageCause() {
    learn(UnarmedPower.class, 1);
    FakeLiving burned = zombie();
    burned.setLastDamage(new EngineLastDamage(DamageCause.FIRE_TICK, null, 1D));
    adapt.post(death(burned));
    assertEquals(0D, profile.getData().getStat("unarmed.power.unarmed-kills"));

    FakeLiving punched = zombie();
    punched.setLastDamage(new EngineLastDamage(DamageCause.ENTITY_ATTACK, player, 4D));
    adapt.post(death(punched));
    assertEquals(1D, profile.getData().getStat("unarmed.power.unarmed-kills"));
  }

  @Test
  void glassCannonKillStatFollowsTheLastDamageCause() {
    learn(UnarmedGlassCannon.class, 1);
    FakeLiving burned = zombie();
    burned.setLastDamage(new EngineLastDamage(DamageCause.FIRE_TICK, null, 1D));
    adapt.post(death(burned));
    assertEquals(0D, profile.getData().getStat("unarmed.glass-cannon.naked-kills"));

    FakeLiving punched = zombie();
    punched.setLastDamage(new EngineLastDamage(DamageCause.ENTITY_ATTACK, player, 4D));
    adapt.post(death(punched));
    assertEquals(1D, profile.getData().getStat("unarmed.glass-cannon.naked-kills"));
  }

  @Test
  void batteringChargeKillStatFollowsTheLastDamageCause() throws Exception {
    UnarmedBatteringCharge charge = learn(UnarmedBatteringCharge.class, 1);
    FakeLiving burned = zombie();
    markCharged(charge, burned);
    burned.setLastDamage(new EngineLastDamage(DamageCause.FIRE_TICK, null, 1D));
    adapt.post(death(burned));
    assertEquals(0D, profile.getData().getStat("unarmed.battering-charge.charge-kills"));

    FakeLiving punched = zombie();
    markCharged(charge, punched);
    punched.setLastDamage(new EngineLastDamage(DamageCause.ENTITY_ATTACK, player, 4D));
    adapt.post(death(punched));
    assertEquals(1D, profile.getData().getStat("unarmed.battering-charge.charge-kills"));
  }

  @SuppressWarnings("unchecked")
  private void markCharged(UnarmedBatteringCharge charge, FakeLiving victim) throws Exception {
    Field marks = UnarmedBatteringCharge.class.getDeclaredField("chargeHitMarks");
    marks.setAccessible(true);
    Class<?> markType = Class.forName(UnarmedBatteringCharge.class.getName() + "$ChargeMark");
    Constructor<?> constructor = markType.getDeclaredConstructor(UUID.class, long.class);
    constructor.setAccessible(true);
    ((Map<UUID, Object>) marks.get(charge)).put(victim.uuid(), constructor.newInstance(player.uuid(), System.currentTimeMillis() + 60_000L));
  }

  private FakeLiving boundGolem() {
    FakeLiving golem = new FakeLiving(Id.minecraft("iron_golem"), new Loc(platform.world(), 3.5D, 61D, 0.5D));
    golem.data().set(GolemState.KEY, "{}");
    return golem;
  }

  private FakeLiving zombie() {
    FakeLiving zombie = new FakeLiving(Id.minecraft("zombie"), new Loc(platform.world(), 1.5D, 61D, 0.5D));
    zombie.trait(EntityTrait.MONSTER);
    return zombie;
  }

  private EntityDamageByEntityEvent punch(FakeLiving victim, double damage) {
    return new EntityDamageByEntityEvent(victim, player, player, DamageCause.ENTITY_ATTACK, PLAYER_ATTACK, damage, false, null);
  }

  private EntityDeathEvent death(FakeLiving victim) {
    return new EntityDeathEvent(victim, player, player, player, DamageCause.ENTITY_ATTACK, List.of(), 0);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    Skill<?> skill = AdaptCore.get().getAdaptServer().getSkillRegistry().getSkill("unarmed");
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (type.isInstance(adaptation)) {
        profile.getData().getSkillLine("unarmed").setAdaptation(adaptation, level);
        return type.cast(adaptation);
      }
    }
    throw new AssertionError("No adaptation " + type.getSimpleName());
  }
}
