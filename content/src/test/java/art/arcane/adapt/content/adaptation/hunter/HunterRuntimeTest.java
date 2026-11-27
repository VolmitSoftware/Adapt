package art.arcane.adapt.content.adaptation.hunter;

import art.arcane.adapt.engine.platform.EngineLastDamage;
import art.arcane.adapt.AdaptCore;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.ReceiveCancelledEvents;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.content.skill.SkillHunter;
import art.arcane.adapt.content.golem.GolemState;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.item.ItemRef;
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

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HunterRuntimeTest {
  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Hunter", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_sword", 250));
    adapt = TestAdapt.start(platform, List.of(SkillHunter.class));
    profile = adapt.join(player);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertTrue(adapt.handlerFailures().isEmpty(), () -> String.valueOf(adapt.handlerFailures()));
  }

  @Test
  void hunterCreditsThePlayersOwnKill() {
    adapt.post(death(zombie(), List.of()));

    assertEquals(1D, profile.getData().getStat("killed.kills"));
    assertEquals(1D, profile.getData().getStat("killed.monsters"));
  }

  @Test
  void hunterPaysNothingForAGolemKill() {
    FakeLiving victim = zombie();
    victim.setLastDamage(new EngineLastDamage(DamageCause.ENTITY_ATTACK, boundGolem(), 4D));

    adapt.post(death(victim, List.of()));

    assertEquals(0D, profile.getData().getStat("killed.kills"));
  }

  @Test
  void blockDropTransferIgnoresAlreadyClaimedDrops() throws Exception {
    Method handler = HunterDropToInventory.class.getDeclaredMethod("on", BlockDropItemEvent.class);
    Handle handle = handler.getAnnotation(Handle.class);

    assertNotNull(handle);
    assertEquals(EventPriority.HIGHEST, handle.priority());
    assertNull(handler.getAnnotation(ReceiveCancelledEvents.class));
  }

  @Test
  void everyDamageHandlerNeverReceivesAlreadyCancelledDamage() throws Exception {
    for (Class<?> adaptationType : List.of(HunterPredatorFocus.class, HunterBigGameHunter.class, HunterBloodTrail.class)) {
      Method handler = adaptationType.getDeclaredMethod("on", EntityDamageByEntityEvent.class);
      assertNotNull(handler.getAnnotation(Handle.class), adaptationType.getSimpleName());
      assertNull(handler.getAnnotation(ReceiveCancelledEvents.class), adaptationType.getSimpleName());
    }
  }

  @Test
  void mobDropsGoToTheKillersInventory() {
    learn(HunterDropToInventory.class, 1);

    EntityDeathEvent event = adapt.post(death(zombie(), List.of(FakeItem.of("leather"))));

    assertTrue(event.drops().isEmpty());
    assertTrue(player.inventory().contains(Id.minecraft("leather"), 1));
    assertEquals(1D, profile.getData().getStat("hunter.drop-to-inv.items-caught"));
  }

  @Test
  void fakePlayerKillsKeepTheirDrops() {
    learn(HunterDropToInventory.class, 1);
    player.setFakePlayer(true);

    EntityDeathEvent event = adapt.post(death(zombie(), List.of(FakeItem.of("leather"))));

    assertEquals(1, event.drops().size());
    assertFalse(player.inventory().contains(Id.minecraft("leather"), 1));
  }

  @Test
  void resistanceAppliesItsBuff() {
    learn(HunterResistance.class, 1);

    adapt.post(hurt());

    assertTrue(player.hasEffect(Id.minecraft("resistance")));
  }

  @Test
  void resistanceDoesNothingWhenTheEffectIsMissing() {
    learn(HunterResistance.class, 1);
    platform.host().removeRegistryId(RegistryKind.EFFECT, Id.minecraft("resistance"));

    adapt.post(hurt());

    assertFalse(player.hasEffect(Id.minecraft("resistance")));
    assertFalse(player.hasEffect(Id.minecraft("hunger")));
  }

  private EntityDamageEvent hurt() {
    return new EntityDamageEvent(player, DamageCause.ENTITY_ATTACK, Id.minecraft("mob_attack"), 4D, base -> base);
  }

  private FakeLiving boundGolem() {
    FakeLiving golem = new FakeLiving(Id.minecraft("iron_golem"), new Loc(platform.world(), 3.5D, 61D, 0.5D));
    golem.data().set(GolemState.KEY, "{}");
    return golem;
  }

  private FakeLiving zombie() {
    FakeLiving zombie = new FakeLiving(Id.minecraft("zombie"), new Loc(platform.world(), 2.5D, 61D, 0.5D));
    zombie.trait(EntityTrait.MONSTER);
    return zombie;
  }

  private EntityDeathEvent death(FakeLiving victim, List<ItemRef> drops) {
    return new EntityDeathEvent(victim, player, player, player, DamageCause.ENTITY_ATTACK, drops, 0);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    Skill<?> skill = AdaptCore.get().getAdaptServer().getSkillRegistry().getSkill("hunter");
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (type.isInstance(adaptation)) {
        profile.getData().getSkillLine("hunter").setAdaptation(adaptation, level);
        return type.cast(adaptation);
      }
    }
    throw new AssertionError("No adaptation " + type.getSimpleName());
  }
}
