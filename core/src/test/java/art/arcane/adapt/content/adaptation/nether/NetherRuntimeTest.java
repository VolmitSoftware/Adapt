package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillNether;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.entity.BlockedDamage;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByBlockEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.Environment;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class NetherRuntimeTest {
  @TempDir
  Path data;

  private TestPlatform platform;
  private FakeWorld world;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    world = platform.world();
    world.environment(Environment.NETHER, 1L);
    player = platform.player("Strider", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillNether.class));
    profile = adapt.join(player);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    Skill<?> skill = SkillRegistry.skills.get("nether");
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (type.isInstance(adaptation)) {
        profile.getData().getSkillLine("nether").setAdaptation(adaptation, level);
        return type.cast(adaptation);
      }
    }
    throw new AssertionError("No adaptation " + type.getSimpleName());
  }

  @Test
  void skillRegistersEveryNetherAdaptation() {
    assertThat(SkillRegistry.skills.get("nether").getAdaptations()).hasSize(14);
  }

  @Test
  void witherSkeletonKillCountsTowardNetherKills() {
    FakeLiving skeleton = world.add(new FakeLiving(Id.minecraft("wither_skeleton"), new Loc(world, 3D, 61D, 0D)));

    adapt.post(new EntityDeathEvent(skeleton, player, player, player, DamageCause.ENTITY_ATTACK, new ArrayList<>(), 5));

    assertThat(profile.getData().getStat("nether.kills")).isEqualTo(1D);
    assertThat(profile.getData().getStat("nether.skeleton.kills")).isEqualTo(1D);
  }

  @Test
  void witherHarvestAddsBonusDropsToTheDeath() {
    learn(NetherWitherHarvest.class, 4);
    FakeLiving skeleton = world.add(new FakeLiving(Id.minecraft("wither_skeleton"), new Loc(world, 3D, 61D, 0D)));

    EntityDeathEvent death = adapt.post(new EntityDeathEvent(skeleton, player, player, player, DamageCause.ENTITY_ATTACK,
        new ArrayList<>(), 5));

    assertThat(death.drops()).extracting(drop -> drop.type().path()).contains("bone", "coal");
    assertThat(profile.getData().getStat("nether.wither-harvest.skeletons-harvested")).isEqualTo(1D);
  }

  @Test
  void skullTossThrowsAMarkedWitherSkull() {
    learn(NetherSkullYeet.class, 1);
    player.setGameMode(GameMode.SURVIVAL);
    FakeItem skulls = FakeItem.of("wither_skeleton_skull");
    skulls.setAmount(3);
    player.holding(skulls);

    PlayerInteractEvent click = adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_AIR, Hand.MAIN_HAND,
        skulls, null, null, null, UseResult.DENY, UseResult.DEFAULT));

    assertThat(click.isCancelled()).isTrue();
    EntityRef skull = world.entities().stream().filter(entity -> entity.type().equals(Id.minecraft("wither_skull")))
        .findFirst().orElseThrow();
    assertThat(NetherSkullYeet.isThrownSkull(skull)).isTrue();
    assertThat(player.inventory().mainHand().amount()).isEqualTo(2);
    assertThat(profile.getData().getStat("nether.skull-yeet.skulls-thrown")).isEqualTo(1D);
  }

  @Test
  void ashwalkerUsesTheDamagingBlockForSoulFire() {
    learn(NetherAshwalker.class, 3);
    world.set(9, 61, 9, "soul_fire");
    EntityDamageEvent hit = adapt.post(new EntityDamageByBlockEvent(player, world.blockAt(9, 61, 9), DamageCause.FIRE,
        Id.minecraft("in_fire"), 10D, FinalDamage.IDENTITY, BlockedDamage.NONE));

    assertThat(hit.isCancelled()).isFalse();
    assertThat(hit.damage()).isCloseTo(2D, within(1.0E-9D));
  }
}
