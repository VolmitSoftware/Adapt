package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.engine.mutation.engine.MutationAdaptation;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.blocking.upgraded.LivingWall;
import art.arcane.adapt.content.skill.SkillBlocking;
import art.arcane.adapt.content.skill.SkillTaming;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.testkit.FakeItem;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Blocking and Taming in either order: pets behind the raised shield or the planted wall are covered from shots. */
class ShieldbearersPackTest {
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
    player = platform.player("Bulwark", 0.5D, 61D, 0.5D);
    player.holding(new FakeItem(Id.minecraft("shield"), 1));
    adapt = TestAdapt.start(platform, List.of(SkillBlocking.class, SkillTaming.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void theSignatureReplacesConvergenceWithNoCooldown() {
    kit.mutate("blocking", "taming");
    assertThat(kit.adaptation("blocking", "taming", Recipe.CONVERGENCE).getSignature()).isInstanceOf(ShieldbearersPack.class);
    assertThat(kit.adaptation("blocking", "taming", Recipe.CONVERGENCE).getCooldownMillis(1)).isZero();
  }

  @Test
  void aShotFromTheFrontAtAPetBehindTheRaisedShieldIsStopped() {
    kit.mutate("blocking", "taming");
    kit.learn("blocking", "taming", Recipe.CONVERGENCE, 1);
    player.setBlocking(true);
    PairMob wolf = PairMob.pet(platform, player, 0.5D, -1.5D);

    assertThat(shoot(wolf, -1D).isCancelled()).isTrue();
    assertThat(shoot(wolf, -1D).isCancelled()).isTrue();
    assertThat(player.heardSounds()).isNotEmpty();
  }

  @Test
  void tamingAsLeadCoversThePackToo() {
    kit.mutate("taming", "blocking");
    kit.learn("taming", "blocking", Recipe.CONVERGENCE, 1);
    player.setBlocking(true);
    PairMob wolf = PairMob.pet(platform, player, 0.5D, -1.5D);

    assertThat(shoot(wolf, -1D).isCancelled()).isTrue();
  }

  @Test
  void noCoverWithoutTheShieldUpFromBehindOrInFront() {
    kit.mutate("blocking", "taming");
    kit.learn("blocking", "taming", Recipe.CONVERGENCE, 1);
    PairMob behind = PairMob.pet(platform, player, 0.5D, -1.5D);
    PairMob ahead = PairMob.pet(platform, player, 0.5D, 2.5D);

    assertThat(shoot(behind, -1D).isCancelled()).isFalse();
    player.setBlocking(true);
    assertThat(shoot(behind, 1D).isCancelled()).isFalse();
    assertThat(shoot(ahead, -1D).isCancelled()).isFalse();
  }

  @Test
  void someoneElsesPetOrAnyOtherMobBehindYouIsNotCovered() {
    kit.mutate("blocking", "taming");
    kit.learn("blocking", "taming", Recipe.CONVERGENCE, 3);
    player.setBlocking(true);
    PairMob stray = PairMob.pet(platform, player, 0.5D, -1.5D);
    stray.setTameOwner(UUID.randomUUID());
    PairMob cow = PairMob.animal(platform, Id.minecraft("cow"), 1.0D, -1.5D);

    assertThat(shoot(stray, -1D).isCancelled()).isFalse();
    assertThat(shoot(cow, -1D).isCancelled()).isFalse();
  }

  @Test
  void nothingIsCoveredWithoutTheLearnedAdaptation() {
    player.setBlocking(true);
    PairMob wolf = PairMob.pet(platform, player, 0.5D, -1.5D);
    assertThat(shoot(wolf, -1D).isCancelled()).isFalse();

    kit.mutate("blocking", "taming");
    assertThat(shoot(wolf, -1D).isCancelled()).isFalse();
  }

  @Test
  void coveredPetsHealEachSecondAndHigherLevelsCoverDeeperAndHealMore() {
    kit.mutate("blocking", "taming");
    MutationAdaptation convergence = kit.learn("blocking", "taming", Recipe.CONVERGENCE, 1);
    ShieldbearersPack pack = (ShieldbearersPack) convergence.getSignature();
    player.setBlocking(true);
    PairMob near = PairMob.pet(platform, player, 0.5D, -1.5D);
    PairMob deep = PairMob.pet(platform, player, 0.5D, -4.5D);
    near.setHealth(10D);
    deep.setHealth(10D);

    pack.onTick();
    platform.scheduler().advance(2);
    assertThat(near.health()).isGreaterThan(10D);
    assertThat(deep.health()).isEqualTo(10D);
    assertThat(shoot(deep, -1D).isCancelled()).isFalse();

    kit.learn("blocking", "taming", Recipe.CONVERGENCE, 3);
    pack.onTick();
    platform.scheduler().advance(2);
    assertThat(deep.health()).isGreaterThan(10D);
    assertThat(shoot(deep, -1D).isCancelled()).isTrue();
    assertThat(pack.healPerSecond(3)).isGreaterThan(pack.healPerSecond(2)).isGreaterThan(pack.healPerSecond(1));
    assertThat(pack.depth(3)).isGreaterThan(pack.depth(1));
  }

  @Test
  void aPlantedBulwarkKeepsCoveringWithTheShieldOutOfHandUntilItComesDown() {
    kit.mutate("blocking", "taming");
    kit.learn("blocking", "taming", Recipe.CONVERGENCE, 1);
    livingWall(1);
    player.holding(FakeItem.tool("shield", 336));
    PairMob wolf = PairMob.pet(platform, player, 0.5D, 0.0D);
    PairMob aside = PairMob.pet(platform, player, 6.5D, 0.0D);
    plant();
    player.moveTo(new Loc(platform.world(), 8.5D, 61D, 8.5D));

    assertThat(shoot(wolf, -1D).isCancelled()).isTrue();
    assertThat(shoot(wolf, 1D).isCancelled()).isFalse();
    assertThat(shoot(aside, -1D).isCancelled()).isFalse();

    platform.scheduler().advance(300);
    assertThat(shoot(wolf, -1D).isCancelled()).isFalse();
  }

  @Test
  void petsAreNotHealedWithTheShieldDownAndNoWallPlanted() {
    kit.mutate("blocking", "taming");
    ShieldbearersPack pack = (ShieldbearersPack) kit.learn("blocking", "taming", Recipe.CONVERGENCE, 3).getSignature();
    livingWall(1);
    PairMob wolf = PairMob.pet(platform, player, 0.5D, -1.5D);
    wolf.setHealth(10D);

    pack.onTick();
    platform.scheduler().advance(2);

    assertThat(wolf.health()).isEqualTo(10D);
  }

  private LivingWall livingWall(int level) {
    LivingWall wall = PairParts.owned(Id.adapt("blocking/upgrade/living_wall"), LivingWall.class);
    profile.getData().getSkillLine("blocking").setAdaptation(wall, level);
    return wall;
  }

  private void plant() {
    player.setBlocking(true);
    player.setSneaking(true);
    adapt.post(new PlayerToggleSneakEvent(player, true));
    platform.scheduler().advance(21);
    player.setSneaking(false);
    adapt.post(new PlayerToggleSneakEvent(player, false));
    player.setBlocking(false);
  }

  private EntityDamageByEntityEvent shoot(PairMob pet, double zVelocity) {
    FakeProjectile arrow = platform.world().add(new FakeProjectile(Id.minecraft("arrow"),
        new Loc(platform.world(), pet.location().x(), 62D, pet.location().z() - (zVelocity * 3D))));
    arrow.setVelocity(new Vec3(0D, 0D, zVelocity));
    return adapt.post(new EntityDamageByEntityEvent(pet, arrow, arrow, DamageCause.PROJECTILE, Id.minecraft("arrow"), 4D,
        false, FinalDamage.IDENTITY));
  }
}
