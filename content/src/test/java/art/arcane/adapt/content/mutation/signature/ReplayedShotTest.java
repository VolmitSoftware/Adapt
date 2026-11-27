package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillChronos;
import art.arcane.adapt.content.skill.SkillRanged;
import art.arcane.adapt.platform.entity.ArrowPickup;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityShootBowEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
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

/** Chronos with Ranged: a fully drawn shot fires again later from where it left, owned by nobody. */
class ReplayedShotTest {
  private static final Id ARROW = Id.minecraft("arrow");
  private static final Vec3 PATH = new Vec3(2.5D, 0.1D, 0D);

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private MutationKit kit;
  private Loc origin;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Archer", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("bow", 384));
    adapt = TestAdapt.start(platform, List.of(SkillChronos.class, SkillRanged.class));
    AdaptPlayer profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
    origin = new Loc(platform.world(), 0.5D, 62.5D, 0.5D);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void aFullyDrawnShotIsReplayedOnTheSamePathWithoutAnArrowOrShooter() {
    kit.mutate("chronos", "ranged");
    kit.learn("chronos", "ranged", Recipe.CONVERGENCE, 1);

    shoot(1F);
    platform.scheduler().advance(29);
    assertThat(replays()).isEmpty();
    platform.scheduler().advance(2);

    List<ProjectileRef> replays = replays();
    assertThat(replays).hasSize(1);
    ProjectileRef replay = replays.get(0);
    assertThat(replay.shooter()).isNull();
    assertThat(replay.velocity()).isEqualTo(PATH);
    assertThat(replay.location()).isEqualTo(origin);
    assertThat(replay.damage()).isCloseTo(1.2D, within(0.001D));
    assertThat(replay.pickupRule()).isEqualTo(ArrowPickup.DISALLOWED);
    assertThat(replay.isCritical()).isFalse();
  }

  @Test
  void aSpectralShotIsReplayedAsASpectralArrow() {
    kit.mutate("chronos", "ranged");
    kit.learn("chronos", "ranged", Recipe.CONVERGENCE, 1);

    shoot(Id.minecraft("spectral_arrow"), 1F);
    platform.scheduler().advance(31);

    assertThat(replays()).singleElement().extracting(ProjectileRef::type).isEqualTo(Id.minecraft("spectral_arrow"));
  }

  @Test
  void rangedLeadingReplaysAtTheTopLevelsFullDamage() {
    kit.mutate("ranged", "chronos");
    kit.learn("ranged", "chronos", Recipe.CONVERGENCE, 3);

    shoot(1F);
    platform.scheduler().advance(31);

    assertThat(replays()).singleElement().satisfies((ProjectileRef replay) ->
        assertThat(replay.damage()).isCloseTo(2D, within(0.001D)));
  }

  @Test
  void aWeakDrawOrNoMutationReplaysNothing() {
    shoot(1F);
    kit.mutate("chronos", "ranged");
    kit.learn("chronos", "ranged", Recipe.CONVERGENCE, 3);
    shoot(0.6F);
    platform.scheduler().advance(40);

    assertThat(replays()).isEmpty();
  }

  @Test
  void theCooldownAllowsOneReplayAtATime() {
    kit.mutate("chronos", "ranged");
    kit.learn("chronos", "ranged", Recipe.CONVERGENCE, 3);

    shoot(1F);
    shoot(1F);
    platform.scheduler().advance(40);

    assertThat(replays()).hasSize(1);
  }

  @Test
  void theReplayNeverHurtsItsArcherOrWhatTheArcherMayNotHurt() {
    kit.mutate("chronos", "ranged");
    kit.learn("chronos", "ranged", Recipe.CONVERGENCE, 3);
    shoot(1F);
    platform.scheduler().advance(31);
    ProjectileRef replay = replays().get(0);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 4.5D, 62D, 0.5D);

    assertThat(arrowHit(player, replay).isCancelled()).isTrue();
    assertThat(arrowHit(zombie, replay).isCancelled()).isFalse();
    FakeLiving pet = platform.world().add(new FakeLiving(Id.minecraft("wolf"), new Loc(platform.world(), 5.5D, 62D, 0.5D)));
    pet.trait(EntityTrait.TAMEABLE);
    pet.setTameOwner(player.uuid());
    assertThat(arrowHit(pet, replay).isCancelled()).isTrue();
  }

  private void shoot(float force) {
    shoot(ARROW, force);
  }

  private void shoot(Id type, float force) {
    FakeProjectile arrow = platform.world().add(new FakeProjectile(type, origin));
    arrow.setShooter(player);
    arrow.setVelocity(PATH);
    arrow.setDamage(2D);
    adapt.post(new EntityShootBowEvent(player, player.inventory().mainHand(), new FakeItem(ARROW, 1), arrow,
        Hand.MAIN_HAND, force, true));
  }

  private List<ProjectileRef> replays() {
    return platform.world().entities().stream()
        .filter((EntityRef entity) -> entity instanceof ProjectileRef arrow && arrow.shooter() == null)
        .map(ProjectileRef.class::cast).toList();
  }

  private EntityDamageByEntityEvent arrowHit(EntityRef target, ProjectileRef arrow) {
    return adapt.post(new EntityDamageByEntityEvent(target, arrow, null, DamageCause.PROJECTILE,
        Id.minecraft("arrow"), 2D, false, FinalDamage.IDENTITY));
  }
}
