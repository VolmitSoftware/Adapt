package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.content.adaptation.tragoul.upgraded.CrimsonTide;
import art.arcane.adapt.content.skill.SkillNether;
import art.arcane.adapt.content.skill.SkillTragOul;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.xp.MobOrigin;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
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

/** TragOul and Nether replace Convergence with Wither Blood: kills spread Wither, wither skeletons fill the reservoir. */
class WitherBloodTest {
  private static final Id WITHER = Id.minecraft("wither");

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private MutationKit kit;
  private AdaptPlayer profile;
  private CrimsonTide tide;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Lich", 0.5D, 61D, 0.5D);
    player.setAttributeBase(Attributes.MAX_HEALTH, 20D);
    player.setHealth(6D);
    adapt = TestAdapt.start(platform, List.of(SkillTragOul.class, SkillNether.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
    tide = SkillHost.of(kit.registry().getSkill("tragoul")).getUpgrades().stream().filter(CrimsonTide.class::isInstance)
        .map(CrimsonTide.class::cast).findFirst().orElseThrow();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void aKillWithersTheHostilesAroundTheCorpse() {
    kit.mutate("tragoul", "nether");
    kit.learn("tragoul", "nether", Recipe.CONVERGENCE, 3);
    assertThat(kit.adaptation("tragoul", "nether", Recipe.CONVERGENCE).getSignature()).isInstanceOf(WitherBlood.class);
    FakeLiving near = MutationKit.zombie(platform.world(), 4D, 61D, 4D);
    FakeLiving far = MutationKit.zombie(platform.world(), 20D, 61D, 20D);
    FakeLiving cow = platform.world().add(new FakeLiving(Id.minecraft("cow"), new Loc(platform.world(), 3D, 61D, 3D)));
    cow.trait(EntityTrait.ANIMAL);

    kill(MutationKit.zombie(platform.world(), 3D, 61D, 3D));

    assertThat(near.hasEffect(WITHER)).isTrue();
    assertThat(near.effect(WITHER).amplifier()).isZero();
    assertThat(far.hasEffect(WITHER)).isFalse();
    assertThat(cow.hasEffect(WITHER)).isFalse();
    assertThat(player.health()).isCloseTo(6D, within(0.001D));
    assertThat(player.heardSounds()).isNotEmpty();
  }

  @Test
  void aWitherSkeletonKillFillsTheBloodReservoir() {
    kit.mutate("tragoul", "nether");
    kit.learn("tragoul", "nether", Recipe.CONVERGENCE, 3);
    holdReservoir();

    kill(skeleton());

    assertThat(tide.blood(player)).isCloseTo(8D, within(0.001D));
    assertThat(player.health()).isCloseTo(6D, within(0.001D));
  }

  @Test
  void withoutAReservoirAWitherSkeletonKillStoresNothing() {
    kit.mutate("tragoul", "nether");
    kit.learn("tragoul", "nether", Recipe.CONVERGENCE, 3);

    kill(skeleton());

    assertThat(tide.blood(player)).isZero();
    assertThat(player.health()).isCloseTo(6D, within(0.001D));
  }

  @Test
  void eitherSkillMayLead() {
    kit.mutate("nether", "tragoul");
    kit.learn("nether", "tragoul", Recipe.CONVERGENCE, 1);
    holdReservoir();

    kill(skeleton());

    assertThat(tide.blood(player)).isCloseTo(4D, within(0.001D));
  }

  @Test
  void nothingHappensWithoutTheLearnedAdaptation() {
    FakeLiving near = MutationKit.zombie(platform.world(), 4D, 61D, 4D);
    kill(skeleton());
    kit.mutate("tragoul", "nether");
    holdReservoir();
    kill(skeleton());

    assertThat(near.hasEffect(WITHER)).isFalse();
    assertThat(tide.blood(player)).isZero();
  }

  @Test
  void theNextKillWaitsForTheCooldown() {
    kit.mutate("tragoul", "nether");
    kit.learn("tragoul", "nether", Recipe.CONVERGENCE, 3);
    holdReservoir();
    kill(skeleton());

    kill(skeleton());

    assertThat(tide.blood(player)).isCloseTo(8D, within(0.001D));
  }

  @Test
  void aSpawnerWitherSkeletonPaysNothing() {
    kit.mutate("tragoul", "nether");
    kit.learn("tragoul", "nether", Recipe.CONVERGENCE, 3);
    FakeLiving near = MutationKit.zombie(platform.world(), 4D, 61D, 4D);
    FakeLiving farmed = skeleton();
    MobOrigin.record(farmed, CreatureSpawnEvent.Reason.SPAWNER);
    holdReservoir();

    kill(farmed);

    assertThat(tide.blood(player)).isZero();
    assertThat(near.hasEffect(WITHER)).isFalse();
  }

  @Test
  void witherImmuneMobsDoNotTakeUpTheSpread() {
    kit.mutate("tragoul", "nether");
    kit.learn("tragoul", "nether", Recipe.CONVERGENCE, 1);
    for (int index = 0; index < 4; index++) {
      skeleton();
    }
    FakeLiving zombie = MutationKit.zombie(platform.world(), 4D, 61D, 4D);

    kill(MutationKit.zombie(platform.world(), 3D, 61D, 3D));

    assertThat(zombie.hasEffect(WITHER)).isTrue();
  }

  private void holdReservoir() {
    profile.getData().getSkillLine("tragoul").setAdaptation(tide, 3);
  }

  private FakeLiving skeleton() {
    FakeLiving skeleton = new FakeLiving(WitherBlood.WITHER_SKELETON, new Loc(platform.world(), 3D, 61D, 3D));
    skeleton.trait(EntityTrait.MONSTER);
    skeleton.trait(EntityTrait.ENEMY);
    skeleton.trait(EntityTrait.SKELETON);
    return platform.world().add(skeleton);
  }

  private void kill(FakeLiving victim) {
    victim.setDead(true);
    adapt.post(new EntityDeathEvent(victim, player, player, player, DamageCause.ENTITY_ATTACK, List.of(), 0));
  }
}
