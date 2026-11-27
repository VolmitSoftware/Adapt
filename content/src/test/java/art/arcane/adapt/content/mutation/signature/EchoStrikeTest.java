package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.engine.mutation.engine.MutationAdaptation;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillChronos;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.common.format.C;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** The reference signature: Chronos with Swords, either leading, replaces Convergence with Echo Strike. */
class EchoStrikeTest {
  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private MutationKit kit;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Keeper", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_sword", 250));
    adapt = TestAdapt.start(platform, List.of(SkillChronos.class, SkillSwords.class));
    AdaptPlayer profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void theSignatureReplacesConvergenceAndKeepsItsPrice() {
    kit.mutate("chronos", "swords");
    MutationAdaptation convergence = kit.adaptation("chronos", "swords", Recipe.CONVERGENCE);

    assertThat(convergence.getSignature()).isInstanceOf(EchoStrike.class);
    assertThat(convergence.getBlocks()).isEmpty();
    assertThat(C.stripColor(convergence.getDisplayName())).isEqualTo("Echo Strike");
    assertThat(convergence.getDescription()).startsWith("Every third sword hit");
    assertThat(List.of(convergence.getCostFor(1), convergence.getCostFor(2), convergence.getCostFor(3)))
        .isEqualTo(Recipe.CONVERGENCE.price().costs());
    assertThat(convergence.getCooldownMillis(3)).isZero();
    StatLines stats = new StatLines();
    convergence.addLevelStats(3, stats);
    assertThat(stats.lines()).hasSize(3);
    assertThat(kit.mutated("swords", "chronos").getMutationAdaptation(Recipe.CONVERGENCE).getSignature())
        .isInstanceOf(EchoStrike.class);
  }

  @Test
  void everyThirdSwordHitEchoesASecondLaterWithTheLevelsShare() {
    kit.mutate("chronos", "swords");
    kit.learn("chronos", "swords", Recipe.CONVERGENCE, 3);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    kit.hit(player, zombie, 6D, false);
    kit.hit(player, zombie, 6D, false);
    platform.scheduler().advance(25);
    assertThat(zombie.damageTaken()).isZero();

    kit.hit(player, zombie, 8D, false);
    platform.scheduler().advance(10);
    assertThat(zombie.damageTaken()).isZero();
    platform.scheduler().advance(15);

    assertThat(zombie.damageTaken()).isCloseTo(4D, within(0.001D));
    assertThat(player.heardSounds()).isNotEmpty();
  }

  @Test
  void swordsLeadingEchoesTheSameWay() {
    kit.mutate("swords", "chronos");
    kit.learn("swords", "chronos", Recipe.CONVERGENCE, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    for (int hit = 0; hit < 3; hit++) {
      kit.hit(player, zombie, 10D, false);
    }
    platform.scheduler().advance(25);

    assertThat(zombie.damageTaken()).isCloseTo(3.5D, within(0.001D));
  }

  @Test
  void sweepHitsDoNotCountTowardTheEcho() {
    kit.mutate("chronos", "swords");
    kit.learn("chronos", "swords", Recipe.CONVERGENCE, 3);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);
    FakeLiving other = MutationKit.zombie(platform.world(), 3.5D, 61D, 0.5D);

    kit.hit(player, zombie, 6D, false);
    for (int sweep = 0; sweep < 4; sweep++) {
      adapt.post(new EntityDamageByEntityEvent(other, player, player, DamageCause.ENTITY_SWEEP_ATTACK,
          Id.minecraft("player_attack"), 1D, false, FinalDamage.IDENTITY));
    }
    platform.scheduler().advance(40);

    assertThat(zombie.damageTaken()).isZero();
    assertThat(other.damageTaken()).isZero();
  }

  @Test
  void nothingEchoesWithoutTheLearnedAdaptation() {
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);
    for (int hit = 0; hit < 3; hit++) {
      kit.hit(player, zombie, 6D, false);
    }
    kit.mutate("chronos", "swords");
    for (int hit = 0; hit < 3; hit++) {
      kit.hit(player, zombie, 6D, false);
    }
    platform.scheduler().advance(40);

    assertThat(zombie.damageTaken()).isZero();
  }
}
