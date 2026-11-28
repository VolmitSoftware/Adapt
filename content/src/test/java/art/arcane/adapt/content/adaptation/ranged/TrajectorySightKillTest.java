package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.content.skill.SkillRanged;
import art.arcane.adapt.engine.platform.EngineLastDamage;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.EntityShootBowEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerStopUsingItemEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeProjectile;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrajectorySightKillTest {
  private static final String KILLS = "ranged.trajectory-sight.kills-while-aiming";

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
    player = platform.player("Marksman", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("bow", 384));
    adapt = TestAdapt.start(platform, List.of(SkillRanged.class));
    profile = adapt.join(player);
    RangedTrajectorySight sight = adapt.core().getAdaptServer().getSkillRegistry().getSkill("ranged").getAdaptations().stream()
        .filter(RangedTrajectorySight.class::isInstance).map(RangedTrajectorySight.class::cast).findFirst().orElseThrow();
    profile.getData().getSkillLine("ranged").setAdaptation(sight, 5);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void aBowReleasedFromTheAimingPreviewCreditsItsKill() {
    draw();
    adapt.post(new PlayerStopUsingItemEvent(player, player.inventory().mainHand(), 40));
    FakeProjectile arrow = shoot();

    kill(arrow);

    assertThat(profile.getData().getStat(KILLS)).isEqualTo(1D);
  }

  @Test
  void aBowDrawnAndLoweredWithoutShootingEndsTheSession() {
    draw();
    adapt.post(new PlayerStopUsingItemEvent(player, player.inventory().mainHand(), 3));
    platform.scheduler().advance(2);
    FakeProjectile arrow = shoot();

    kill(arrow);

    assertThat(profile.getData().getStat(KILLS)).isZero();
  }

  private void draw() {
    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_AIR, Hand.MAIN_HAND,
        player.inventory().mainHand(), null, null, null, UseResult.DEFAULT, UseResult.DEFAULT));
  }

  private FakeProjectile shoot() {
    FakeProjectile arrow = world.add(new FakeProjectile(Id.minecraft("arrow"), new Loc(world, 0.5D, 62.5D, 1.5D)));
    arrow.setShooter(player);
    arrow.setVelocity(new Vec3(0D, 0D, 1D));
    adapt.post(new EntityShootBowEvent(player, player.inventory().mainHand(), FakeItem.of("arrow"), arrow,
        Hand.MAIN_HAND, 1F, true));
    adapt.post(new ProjectileLaunchEvent(arrow));
    return arrow;
  }

  private void kill(FakeProjectile arrow) {
    FakeLiving zombie = world.add(new FakeLiving(Id.minecraft("zombie"), new Loc(world, 0.5D, 61D, 6.5D)) {
      @Override
      public PlayerRef killer() {
        return player;
      }
    });
    zombie.trait(EntityTrait.MONSTER);
    zombie.setLastDamage(new EngineLastDamage(DamageCause.PROJECTILE, arrow, 20D));
    adapt.post(new EntityDeathEvent(zombie, player, arrow, player, DamageCause.PROJECTILE, List.of(), 0));
  }
}
