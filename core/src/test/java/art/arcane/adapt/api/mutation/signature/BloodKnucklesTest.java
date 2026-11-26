package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.content.adaptation.unarmed.upgraded.UnbrokenCombo;
import art.arcane.adapt.content.skill.SkillTragOul;
import art.arcane.adapt.content.skill.SkillUnarmed;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
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

/** Unarmed and TragOul replace Convergence with Blood Knuckles: bare-hand streaks siphon, Unbroken Combo's finisher heals. */
class BloodKnucklesTest {
  private static final int COMBO_STACKS = 6;

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private MutationKit kit;
  private AdaptPlayer profile;
  private FakeLiving zombie;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Knuckles", 0.5D, 61D, 0.5D);
    player.setAttributeBase(Attributes.MAX_HEALTH, 20D);
    player.setHealth(10D);
    adapt = TestAdapt.start(platform, List.of(SkillUnarmed.class, SkillTragOul.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
    zombie = MutationKit.zombie(platform.world(), 0.5D, 61D, 2D);
    zombie.setAttributeBase(Attributes.MAX_HEALTH, 2000D);
    zombie.setHealth(2000D);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void eachStackOfAStreakSiphonsMore() {
    kit.mutate("unarmed", "tragoul");
    kit.learn("unarmed", "tragoul", Recipe.CONVERGENCE, 3);
    assertThat(kit.adaptation("unarmed", "tragoul", Recipe.CONVERGENCE).getSignature()).isInstanceOf(BloodKnuckles.class);

    punch(3);

    assertThat(player.health()).isCloseTo(12.4D, within(0.001D));
  }

  @Test
  void unbrokenCombosFinisherHealsHalfItsDamage() {
    kit.mutate("unarmed", "tragoul");
    kit.learn("unarmed", "tragoul", Recipe.CONVERGENCE, 3);
    learnUnbrokenCombo();
    player.setAttributeBase(Attributes.MAX_HEALTH, 1000D);

    punch(COMBO_STACKS);
    player.setHealth(4D);
    player.setSneaking(true);
    EntityDamageByEntityEvent finisher = kit.hit(player, zombie, 10D, false);

    assertThat(finisher.finalDamage()).isGreaterThan(10D);
    assertThat(player.health()).isCloseTo(4D + (finisher.finalDamage() * 0.5D), within(0.001D));
    assertThat(player.heardSounds()).isNotEmpty();
  }

  @Test
  void aSneakingHitAtAFullStreakHealsNothingWithoutUnbrokenCombo() {
    kit.mutate("unarmed", "tragoul");
    kit.learn("unarmed", "tragoul", Recipe.CONVERGENCE, 3);

    punch(8);
    player.setHealth(4D);
    player.setSneaking(true);
    kit.hit(player, zombie, 10D, false);

    assertThat(player.health()).isLessThan(9D);
  }

  @Test
  void theFinisherHealWaitsForItsCooldown() {
    kit.mutate("unarmed", "tragoul");
    kit.learn("unarmed", "tragoul", Recipe.CONVERGENCE, 3);
    learnUnbrokenCombo();
    punch(COMBO_STACKS);
    player.setSneaking(true);
    kit.hit(player, zombie, 10D, false);
    player.setSneaking(false);

    punch(COMBO_STACKS);
    player.setHealth(4D);
    player.setSneaking(true);
    kit.hit(player, zombie, 10D, false);

    assertThat(player.health()).isLessThan(4D + (COMBO_STACKS * 0.4D) + 0.001D);
  }

  @Test
  void anArmorStandOrAPassiveMobHealsNothing() {
    kit.mutate("unarmed", "tragoul");
    kit.learn("unarmed", "tragoul", Recipe.CONVERGENCE, 3);
    FakeLiving stand = platform.world().add(new FakeLiving(Id.minecraft("armor_stand"), new Loc(platform.world(), 0.5D, 61D, 2D)));
    FakeLiving cow = platform.world().add(new FakeLiving(Id.minecraft("cow"), new Loc(platform.world(), 0.5D, 61D, 2D)));

    for (int hit = 0; hit < 4; hit++) {
      kit.hit(player, stand, 2D, false);
      kit.hit(player, cow, 2D, false);
    }

    assertThat(player.health()).isCloseTo(10D, within(0.001D));
  }

  @Test
  void theSiphonStopsWhenTheSignatureIsTurnedOff() {
    kit.mutate("unarmed", "tragoul");
    kit.learn("unarmed", "tragoul", Recipe.CONVERGENCE, 3);
    kit.adaptation("unarmed", "tragoul", Recipe.CONVERGENCE).getSignature().getConfig().enabled = false;

    punch(3);

    assertThat(player.health()).isCloseTo(10D, within(0.001D));
  }

  @Test
  void siphonStopsAtThePerSecondCap() {
    kit.mutate("unarmed", "tragoul");
    kit.learn("unarmed", "tragoul", Recipe.CONVERGENCE, 1);
    player.setHealth(1D);

    punch(8);

    assertThat(player.health()).isCloseTo(6D, within(0.001D));
  }

  @Test
  void eitherSkillMayLead() {
    kit.mutate("tragoul", "unarmed");
    kit.learn("tragoul", "unarmed", Recipe.CONVERGENCE, 1);

    punch(1);

    assertThat(player.health()).isCloseTo(10.2D, within(0.001D));
  }

  @Test
  void nothingHealsWithoutTheLearnedAdaptation() {
    punch(3);
    kit.mutate("unarmed", "tragoul");
    punch(3);

    assertThat(player.health()).isCloseTo(10D, within(0.001D));
  }

  @Test
  void aSpawnerMobHealsNothing() {
    kit.mutate("unarmed", "tragoul");
    kit.learn("unarmed", "tragoul", Recipe.CONVERGENCE, 3);
    learnUnbrokenCombo();
    MobOrigin.record(zombie, CreatureSpawnEvent.Reason.SPAWNER);

    punch(COMBO_STACKS);
    player.setHealth(4D);
    player.setSneaking(true);
    kit.hit(player, zombie, 10D, false);

    assertThat(player.health()).isCloseTo(4D, within(0.001D));
  }

  private void learnUnbrokenCombo() {
    UnbrokenCombo combo = (UnbrokenCombo) adapt.core().getAdaptServer().getSkillRegistry()
        .getAdaptation("upgraded-unarmed-unbroken-combo");
    profile.getData().getSkillLine("unarmed").setAdaptation(combo, 1);
  }

  private void punch(int hits) {
    for (int hit = 0; hit < hits; hit++) {
      kit.hit(player, zombie, 2D, false);
    }
  }
}
