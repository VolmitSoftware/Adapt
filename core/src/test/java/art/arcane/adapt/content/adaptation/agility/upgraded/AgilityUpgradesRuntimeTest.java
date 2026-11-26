package art.arcane.adapt.content.adaptation.agility.upgraded;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.content.adaptation.agility.AgilityRunRamp;
import art.arcane.adapt.content.skill.SkillAgility;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.Pose;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.player.EntityExhaustionEvent;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSprintEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeProjectile;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.testkit.XpPaid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AgilityUpgradesRuntimeTest {
  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private SkillRegistry registry;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Runner", 0.5D, 64D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillAgility.class, SkillStealth.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void untouchableDoesNothingForAPlayerWhoHasNotMutated() {
    profile.getData().getSkillLine("agility").setAdaptation(upgrade(Untouchable.class), 1);
    player.setSprinting(true);

    EntityDamageByEntityEvent arrow = adapt.post(projectileHit());
    EntityDamageEvent fall = adapt.post(fall(6D));

    assertThat(arrow.isCancelled()).isFalse();
    assertThat(fall.isCancelled()).isFalse();
  }

  @Test
  void slipstreamSpeedStopsAtSixtyPercentAtLevelOneHoweverLongTheRun() {
    mutate();
    Slipstream slipstream = learn(Slipstream.class, 1);

    assertThat(longRun(slipstream, 1)).isCloseTo(0.6D, within(1.0E-6D));
  }

  @Test
  void slipstreamSpeedNeverPassesDoubleSpeedAtTheTopLevel() {
    mutate();
    Slipstream slipstream = learn(Slipstream.class, 3);

    assertThat(slipstream.getMaximumSpeedBonus(2)).isCloseTo(0.8D, within(1.0E-9D));
    assertThat(longRun(slipstream, 3)).isCloseTo(1.0D, within(1.0E-6D));
  }

  @Test
  void slipstreamRunsFeatherfootHigherThanTheMutationKeepsIt() {
    mutate();
    Adaptation<?> featherfoot = registry.getSkill("agility").getAdaptations().stream()
        .filter((Adaptation<?> original) -> original.getName().equals("agility-featherfoot")).findFirst().orElseThrow();
    int kept = featherfoot.getActiveLevel(player);

    learn(Slipstream.class, 1);
    int atFirst = featherfoot.getActiveLevel(player);
    learn(Slipstream.class, 3);

    assertThat(kept).isEqualTo(1);
    assertThat(atFirst).isEqualTo(2);
    assertThat(featherfoot.getActiveLevel(player)).isEqualTo(featherfoot.getMaxLevel());
  }

  @Test
  void skyrunnerWallsRefreshTheChargeOnlyAFewTimesBeforeYouLand() {
    mutate();
    Skyrunner skyrunner = learn(Skyrunner.class, 1);
    player.setOnGround(true);
    run();
    player.setOnGround(false);
    run();
    assertThat(dash()).isTrue();
    int refreshes = 0;
    for (int climb = 0; climb < skyrunner.getConfig().wallRefreshesPerJump + 2; climb++) {
      int wallZ = player.location().blockZ();
      platform.world().set(1, 64, wallZ, "stone");
      player.setSneaking(true);
      adapt.post(new PlayerToggleSneakEvent(player, true));
      platform.world().set(1, 64, wallZ, "air");
      run();
      player.setSneaking(false);
      if (dash()) {
        refreshes++;
      }
    }

    assertThat(refreshes).isEqualTo(skyrunner.getConfig().wallRefreshesPerJump);
  }

  @Test
  void skyrunnerMovesPayNoSkillXp() {
    mutate();
    Skyrunner skyrunner = learn(Skyrunner.class, 1);
    player.setOnGround(true);
    run();
    player.setOnGround(false);
    run();

    assertThat(dash()).isTrue();
    assertThat(XpPaid.by(profile, skyrunner)).isZero();
  }

  @Test
  void slipstreamSprintingBuildsSpeedAndASlideKeepsIt() {
    mutate();
    learn(Slipstream.class, 1);
    player.setSprinting(true);
    player.setOnGround(true);

    run();
    assertThat(player.modifiers(Attributes.MOVEMENT_SPEED)).isNotEmpty();

    adapt.post(new PlayerToggleSneakEvent(player, true));
    player.setSprinting(false);
    adapt.post(new PlayerToggleSprintEvent(player, false));

    assertThat(player.pose()).isEqualTo(Pose.SWIMMING);
    assertThat(player.modifiers(Attributes.MOVEMENT_SPEED)).isNotEmpty();
  }

  @Test
  void slipstreamSprintingCostsNoHunger() {
    mutate();
    learn(Slipstream.class, 1);
    player.setSprinting(true);

    EntityExhaustionEvent event = adapt.post(new EntityExhaustionEvent(player, EntityExhaustionEvent.Reason.SPRINT, 0.1F));

    assertThat(event.exhaustion()).isZero();
  }

  @Test
  void skyrunnerSharesOneMidAirChargeThatAWallRefreshes() {
    mutate();
    Skyrunner skyrunner = learn(Skyrunner.class, 1);
    player.setOnGround(true);
    run();
    player.setOnGround(false);
    run();

    assertThat(dash()).isTrue();
    assertThat(dash()).isFalse();

    platform.world().set(1, 64, 0, "stone");
    player.setSneaking(true);
    adapt.post(new PlayerToggleSneakEvent(player, true));
    player.setSneaking(false);
    player.setVelocity(Vec3.ZERO);
    adapt.post(new PlayerToggleSneakEvent(player, false));

    assertThat(player.velocity().y()).isCloseTo(skyrunner.getWallJumpVelocity(1), within(1.0E-9D));
    assertThat(dash()).isFalse();

    player.setSneaking(true);
    adapt.post(new PlayerToggleSneakEvent(player, true));
    platform.world().set(1, 64, 0, "air");
    run();
    player.setSneaking(false);

    assertThat(dash()).isTrue();
  }

  @Test
  void untouchableDodgesProjectilesWhileSprinting() {
    mutate();
    Untouchable untouchable = learn(Untouchable.class, 1);
    untouchable.getConfig().dodgeChanceBase = 1D;
    player.setSprinting(true);

    assertThat(adapt.post(projectileHit()).isCancelled()).isTrue();
  }

  @Test
  void untouchableDodgesOnlyAShareOfProjectilesNeverAll() {
    mutate();
    Untouchable untouchable = learn(Untouchable.class, 1);
    player.setSprinting(true);
    int dodged = 0;
    for (int shot = 0; shot < 400; shot++) {
      if (adapt.post(projectileHit()).isCancelled()) {
        dodged++;
      }
    }

    assertThat(untouchable.getDodgeChance(1)).isCloseTo(0.5D, within(1.0E-9D));
    assertThat(untouchable.getDodgeChance(3)).isCloseTo(0.9D, within(1.0E-9D));
    assertThat(dodged).isBetween(140, 260);
  }

  @Test
  void untouchableRollsOutOfFallsUpToItsHeightAndNoHigher() {
    mutate();
    learn(Untouchable.class, 1);

    player.setFallDistance(12F);
    boolean rolledTwelve = adapt.post(fall(9D)).isCancelled();
    boolean rolled = player.isSwimming();
    player.setFallDistance(13F);
    boolean rolledThirteen = adapt.post(fall(10D)).isCancelled();
    player.setFallDistance(0F);
    boolean rolledUntracked = adapt.post(fall(40D)).isCancelled();
    learn(Untouchable.class, 3);
    player.setFallDistance(30F);
    boolean rolledThirty = adapt.post(fall(27D)).isCancelled();
    player.setFallDistance(31F);
    boolean rolledThirtyOne = adapt.post(fall(28D)).isCancelled();

    assertThat(rolledTwelve).isTrue();
    assertThat(rolled).isTrue();
    assertThat(rolledThirteen).isFalse();
    assertThat(rolledUntracked).isFalse();
    assertThat(rolledThirty).isTrue();
    assertThat(rolledThirtyOne).isFalse();
  }

  @Test
  void untouchablePaysNoXpForRolledFallsAndLeavesPearlLandingsAlone() {
    mutate();
    Untouchable untouchable = learn(Untouchable.class, 1);

    for (int fall = 0; fall < 20; fall++) {
      adapt.post(fall(6D));
    }
    EntityDamageEvent pearl = adapt.post(new EntityDamageEvent(player, DamageCause.FALL, Id.minecraft("ender_pearl"), 5D,
        FinalDamage.IDENTITY));

    assertThat(XpPaid.by(profile, untouchable)).isZero();
    assertThat(pearl.isCancelled()).isFalse();
  }

  @Test
  void untouchablePaysNoXpForADodgeEvenFromANaturalShooter() {
    mutate();
    Untouchable untouchable = learn(Untouchable.class, 1);
    untouchable.getConfig().dodgeChanceBase = 1D;
    player.setSprinting(true);
    FakeLiving natural = platform.world().add(new FakeLiving(Id.minecraft("skeleton"), new Loc(platform.world(), 0.5D, 64D, 9.5D)));
    MobOrigin.record(natural, CreatureSpawnEvent.Reason.NATURAL);

    boolean dodged = adapt.post(projectileHit(natural)).isCancelled();

    assertThat(dodged).isTrue();
    assertThat(XpPaid.by(profile, untouchable)).isZero();
  }

  @Test
  void skyrunnerGivesTwoMidAirChargesFromLevelTwo() {
    mutate();
    Skyrunner skyrunner = learn(Skyrunner.class, 2);
    player.setOnGround(true);
    run();
    player.setOnGround(false);
    run();

    assertThat(skyrunner.getMidAirCharges(1)).isEqualTo(1);
    assertThat(skyrunner.getMidAirCharges(3)).isEqualTo(2);
    assertThat(dash()).isTrue();
    assertThat(dash()).isTrue();
    assertThat(dash()).isFalse();
  }

  @Test
  void untouchableTurnsAHitIntoSpeed() {
    mutate();
    learn(Untouchable.class, 1);
    FakeLiving zombie = platform.world().add(new FakeLiving(Id.minecraft("zombie"), new Loc(platform.world(), 2.5D, 64D, 0.5D)));

    adapt.post(new EntityDamageByEntityEvent(player, zombie, zombie, DamageCause.ENTITY_ATTACK,
        Id.minecraft("mob_attack"), 3D, false, FinalDamage.IDENTITY));

    assertThat(player.modifiers(Attributes.MOVEMENT_SPEED)).isNotEmpty();
  }

  private double longRun(Slipstream slipstream, int level) {
    AgilityRunRamp ramp = new AgilityRunRamp(slipstream, new HashMap<>(), (PlayerRef runner, double ticks) -> {
    });
    long now = 1_000L;
    for (int update = 0; update < 600; update++) {
      ramp.update(player, slipstream.ramp(level), now += 1_000L);
    }
    return ramp.bonus(player.uuid());
  }

  private boolean dash() {
    player.setVelocity(Vec3.ZERO);
    adapt.post(new PlayerInteractEvent(player, InteractAction.LEFT_CLICK_AIR, Hand.MAIN_HAND, null, null, null, null,
        null, null));
    return player.velocity().lengthSquared() > 0D;
  }

  private void run() {
    Loc from = player.location();
    Loc to = from.add(0D, 0D, 0.2D);
    player.moveTo(to);
    adapt.post(new PlayerMoveEvent(player, from, to));
  }

  private EntityDamageByEntityEvent projectileHit(FakeLiving shooter) {
    FakeProjectile arrow = platform.world().add(new FakeProjectile(Id.minecraft("arrow"), new Loc(platform.world(), 0.5D, 65D, 4.5D)));
    arrow.setShooter(shooter);
    return new EntityDamageByEntityEvent(player, arrow, arrow, DamageCause.PROJECTILE, Id.minecraft("arrow"), 4D, false,
        FinalDamage.IDENTITY);
  }

  private EntityDamageByEntityEvent projectileHit() {
    FakeProjectile arrow = platform.world().add(new FakeProjectile(Id.minecraft("arrow"), new Loc(platform.world(), 0.5D, 65D, 4.5D)));
    return new EntityDamageByEntityEvent(player, arrow, arrow, DamageCause.PROJECTILE, Id.minecraft("arrow"), 4D, false,
        FinalDamage.IDENTITY);
  }

  private EntityDamageEvent fall(double damage) {
    return new EntityDamageEvent(player, DamageCause.FALL, Id.minecraft("fall"), damage, FinalDamage.IDENTITY);
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("agility", "stealth")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("agility"), registry.getSkill("stealth"), "Runner"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A upgrade = upgrade(type);
    profile.getData().getSkillLine("agility").setAdaptation(upgrade, level);
    return upgrade;
  }

  private <A extends Adaptation<?>> A upgrade(Class<A> type) {
    return registry.getSkill("agility").getUpgrades().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
  }
}
