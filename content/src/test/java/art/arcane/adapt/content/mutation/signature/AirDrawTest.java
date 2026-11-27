package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.agility.upgraded.Skyrunner;
import art.arcane.adapt.content.skill.SkillAgility;
import art.arcane.adapt.content.skill.SkillRanged;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityShootBowEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeProjectile;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Ranged and Agility in either order: drawing in the air slows the fall up to a budget, half-drawn air shots fly fully
 * drawn, and an air hit refills Skyrunner once per airtime.
 */
class AirDrawTest {
  private static final Id BOW = Id.minecraft("bow");
  private static final Id SLOW_FALLING = Id.minecraft("slow_falling");

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private AdaptPlayer profile;
  private MutationKit kit;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Archer", 0.5D, 70D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillRanged.class, SkillAgility.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
    player.holding(new FakeItem(BOW, 1));
    player.setActiveItem(player.inventory().mainHand());
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void drawingInTheAirSlowsTheFallOnlyUpToTheAirtimeBudget() {
    kit.mutate("ranged", "agility");
    kit.learn("ranged", "agility", Recipe.CONVERGENCE, 1);
    player.setOnGround(false);
    draw();

    assertThat(hoveredTicks("ranged", "agility", 60)).isBetween(37, 40);
    assertThat(hoveredTicks("ranged", "agility", 10)).isZero();
  }

  @Test
  void agilityAsLeadSlowsTheFallToo() {
    kit.mutate("agility", "ranged");
    kit.learn("agility", "ranged", Recipe.CONVERGENCE, 3);
    player.setOnGround(false);
    draw();

    assertThat(hoveredTicks("agility", "ranged", 100)).isBetween(77, 80);
  }

  @Test
  void drawingAgainInTheSameAirtimeDoesNotRefillTheBudgetButLandingDoes() {
    kit.mutate("ranged", "agility");
    kit.learn("ranged", "agility", Recipe.CONVERGENCE, 1);
    player.setOnGround(false);
    draw();
    assertThat(hoveredTicks("ranged", "agility", 30)).isEqualTo(30);

    player.setActiveItem(FakeItem.empty());
    signature("ranged", "agility").onTick();
    platform.scheduler().advance(1);
    player.setActiveItem(player.inventory().mainHand());
    draw();
    assertThat(hoveredTicks("ranged", "agility", 30)).isBetween(7, 10);

    player.setOnGround(true);
    adapt.post(new PlayerMoveEvent(player, player.location(), player.location()));
    player.setOnGround(false);
    draw();
    assertThat(hoveredTicks("ranged", "agility", 60)).isBetween(37, 40);
  }

  @Test
  void anArrowLoosedInTheAirFromHalfADrawFliesFullyDrawnAndCrits() {
    kit.mutate("ranged", "agility");
    kit.learn("ranged", "agility", Recipe.CONVERGENCE, 1);
    player.setOnGround(false);

    FakeProjectile arrow = loose(0.6F, 1.8D);

    assertThat(arrow.velocity().length()).isCloseTo(3D, within(1.0E-9D));
    assertThat(arrow.isCritical()).isTrue();
    assertThat(player.heardSounds()).isNotEmpty();
  }

  @Test
  void aTappedBowInTheAirLoosesAnOrdinaryArrow() {
    kit.mutate("ranged", "agility");
    kit.learn("ranged", "agility", Recipe.CONVERGENCE, 1);
    player.setOnGround(false);

    FakeProjectile tapped = loose(0.3F, 0.9D);

    assertThat(tapped.velocity().length()).isCloseTo(0.9D, within(1.0E-9D));
    assertThat(tapped.isCritical()).isFalse();
  }

  @Test
  void anArrowHitFromTheAirRefillsSkyrunnersChargesOncePerAirtime() {
    kit.mutate("ranged", "agility");
    kit.learn("ranged", "agility", Recipe.CONVERGENCE, 1);
    Skyrunner skyrunner = SkillHost.of(kit.registry().getSkill("agility")).getUpgrades().stream().filter(Skyrunner.class::isInstance)
        .map(Skyrunner.class::cast).findFirst().orElseThrow();
    profile.getData().getSkillLine("agility").setAdaptation(skyrunner, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 0.5D, 70D, 12.5D);
    takeOff();
    assertThat(dash()).isTrue();
    assertThat(dash()).isFalse();

    arrowHit(zombie);
    assertThat(dash()).isTrue();
    arrowHit(zombie);
    assertThat(dash()).isFalse();

    takeOff();
    assertThat(dash()).isTrue();
    arrowHit(zombie);
    assertThat(dash()).isTrue();
  }

  @Test
  void anArrowHitFromTheGroundRefillsNothing() {
    kit.mutate("ranged", "agility");
    kit.learn("ranged", "agility", Recipe.CONVERGENCE, 1);
    Skyrunner skyrunner = SkillHost.of(kit.registry().getSkill("agility")).getUpgrades().stream().filter(Skyrunner.class::isInstance)
        .map(Skyrunner.class::cast).findFirst().orElseThrow();
    profile.getData().getSkillLine("agility").setAdaptation(skyrunner, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 0.5D, 70D, 12.5D);
    takeOff();
    assertThat(dash()).isTrue();
    player.setOnGround(true);
    arrowHit(zombie);
    player.setOnGround(false);

    assertThat(dash()).isFalse();
  }

  @Test
  void onTheGroundOrWithoutTheLearnedAdaptationNothingChanges() {
    player.setOnGround(false);
    FakeProjectile untrained = loose(0.6F, 0.9D);
    draw();
    assertThat(hoveredTicks("ranged", "agility", 5, false)).isZero();
    assertThat(untrained.velocity().length()).isCloseTo(0.9D, within(1.0E-9D));

    kit.mutate("ranged", "agility");
    kit.learn("ranged", "agility", Recipe.CONVERGENCE, 1);
    player.setOnGround(true);
    FakeProjectile grounded = loose(0.6F, 0.9D);
    assertThat(grounded.velocity().length()).isCloseTo(0.9D, within(1.0E-9D));
    assertThat(grounded.isCritical()).isFalse();
  }

  private void takeOff() {
    player.setOnGround(true);
    adapt.post(new PlayerMoveEvent(player, player.location(), player.location()));
    player.setOnGround(false);
    adapt.post(new PlayerMoveEvent(player, player.location(), player.location()));
  }

  private boolean dash() {
    player.setVelocity(Vec3.ZERO);
    adapt.post(new PlayerInteractEvent(player, InteractAction.LEFT_CLICK_AIR, Hand.MAIN_HAND, null, null, null, null,
        null, null));
    return player.velocity().lengthSquared() > 0D;
  }

  private void arrowHit(FakeLiving target) {
    FakeProjectile arrow = platform.world().add(new FakeProjectile(Id.minecraft("arrow"), target.location()));
    arrow.setShooter(player);
    adapt.post(new EntityDamageByEntityEvent(target, arrow, player, DamageCause.PROJECTILE, Id.minecraft("arrow"), 4D,
        false, FinalDamage.IDENTITY));
    platform.scheduler().advance(1);
  }

  private void draw() {
    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_AIR, Hand.MAIN_HAND, player.inventory().mainHand(),
        null, null, null, UseResult.DEFAULT, UseResult.DEFAULT));
  }

  private int hoveredTicks(String lead, String partner, int ticks) {
    return hoveredTicks(lead, partner, ticks, true);
  }

  /** Ticks out of {@code ticks} in which the slowed fall was granted, letting each grant lapse in between. */
  private int hoveredTicks(String lead, String partner, int ticks, boolean learned) {
    int hovered = 0;
    for (int tick = 0; tick < ticks; tick++) {
      player.removeEffect(SLOW_FALLING);
      if (learned) {
        signature(lead, partner).onTick();
      }
      platform.scheduler().advance(1);
      if (player.hasEffect(SLOW_FALLING)) {
        hovered++;
      }
    }
    return hovered;
  }

  private FakeProjectile loose(float force, double speed) {
    FakeProjectile arrow = platform.world().add(new FakeProjectile(Id.minecraft("arrow"), new Loc(platform.world(), 0.5D, 71.6D, 0.5D)));
    arrow.setShooter(player);
    arrow.setVelocity(new Vec3(0D, 0D, speed));
    adapt.post(new EntityShootBowEvent(player, player.inventory().mainHand(), new FakeItem(Id.minecraft("arrow"), 1), arrow,
        Hand.MAIN_HAND, force, true));
    return arrow;
  }

  private AirDraw signature(String lead, String partner) {
    return (AirDraw) kit.adaptation(lead, partner, Recipe.CONVERGENCE).getSignature();
  }
}
