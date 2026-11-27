package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.engine.mutation.engine.MutationAdaptation;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillBrewing;
import art.arcane.adapt.content.skill.SkillChronos;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;
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

/** Chronos with Brewing: the clock gesture holds beneficial effects aside and gives them back unchanged. */
class PausedPotionsTest {
  private static final Id CLOCK = Id.minecraft("clock");
  private static final Id SPEED = Id.minecraft("speed");
  private static final Id REGENERATION = Id.minecraft("regeneration");
  private static final Id POISON = Id.minecraft("poison");
  private static final Id NIGHT_VISION = Id.minecraft("night_vision");

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private MutationKit kit;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Alchemist", 0.5D, 61D, 0.5D);
    player.holding(new FakeItem(CLOCK, 1));
    adapt = TestAdapt.start(platform, List.of(SkillChronos.class, SkillBrewing.class));
    AdaptPlayer profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void theSignatureReplacesConvergenceInEitherOrder() {
    kit.mutate("chronos", "brewing");
    MutationAdaptation convergence = kit.adaptation("chronos", "brewing", Recipe.CONVERGENCE);

    assertThat(convergence.getSignature()).isInstanceOf(PausedPotions.class);
    assertThat(convergence.getBlocks()).isEmpty();
    assertThat(kit.mutated("brewing", "chronos").getMutationAdaptation(Recipe.CONVERGENCE).getSignature())
        .isInstanceOf(PausedPotions.class);
  }

  @Test
  void theGesturePausesBeneficialEffectsAndTheNextOneGivesThemBackUnchanged() {
    kit.mutate("chronos", "brewing");
    kit.learn("chronos", "brewing", Recipe.CONVERGENCE, 3);
    player.addEffect(EffectInstance.of(SPEED, 2400, 1));
    player.addEffect(EffectInstance.of(POISON, 200, 0));

    assertThat(swap().isCancelled()).isTrue();
    assertThat(player.effect(SPEED)).isNull();
    assertThat(player.effect(POISON).durationTicks()).isEqualTo(200);

    platform.scheduler().advance(100);
    assertThat(player.effect(SPEED)).isNull();
    assertThat(swap().isCancelled()).isTrue();

    EffectInstance back = player.effect(SPEED);
    assertThat(back.durationTicks()).isEqualTo(2400);
    assertThat(back.amplifier()).isEqualTo(1);
    assertThat(player.heardSounds()).isNotEmpty();
  }

  @Test
  void brewingLeadingWorksTheSameWay() {
    kit.mutate("brewing", "chronos");
    kit.learn("brewing", "chronos", Recipe.CONVERGENCE, 1);
    player.addEffect(EffectInstance.of(REGENERATION, 600, 0));

    swap();

    assertThat(player.effect(REGENERATION)).isNull();
  }

  @Test
  void thePauseEndsOnItsOwnAfterTheLevelsLongestPause() {
    kit.mutate("chronos", "brewing");
    kit.learn("chronos", "brewing", Recipe.CONVERGENCE, 1);
    player.addEffect(EffectInstance.of(SPEED, 2400, 0));

    swap();
    platform.scheduler().advance(399);
    assertThat(player.effect(SPEED)).isNull();
    platform.scheduler().advance(2);

    assertThat(player.effect(SPEED).durationTicks()).isEqualTo(2400);
  }

  @Test
  void nothingPausesWithoutTheMutationOrTheLearnedLevel() {
    player.addEffect(EffectInstance.of(SPEED, 2400, 0));
    assertThat(swap().isCancelled()).isFalse();

    kit.mutate("chronos", "brewing");
    assertThat(swap().isCancelled()).isFalse();

    assertThat(player.effect(SPEED)).isNotNull();
  }

  @Test
  void aSecondPauseWaitsForTheCooldown() {
    kit.mutate("chronos", "brewing");
    kit.learn("chronos", "brewing", Recipe.CONVERGENCE, 3);
    player.addEffect(EffectInstance.of(SPEED, 2400, 0));

    swap();
    swap();
    swap();

    assertThat(player.effect(SPEED)).isNotNull();
  }

  @Test
  void harmfulInfiniteAndShortEffectsCannotBeHiddenOrStretched() {
    kit.mutate("chronos", "brewing");
    kit.learn("chronos", "brewing", Recipe.CONVERGENCE, 3);
    player.addEffect(EffectInstance.of(POISON, 600, 1));
    player.addEffect(EffectInstance.of(NIGHT_VISION, EffectInstance.INFINITE, 0));
    player.addEffect(EffectInstance.of(SPEED, 40, 0));

    assertThat(swap().isCancelled()).isFalse();

    assertThat(player.effect(POISON).durationTicks()).isEqualTo(600);
    assertThat(player.effect(NIGHT_VISION).isInfinite()).isTrue();
    assertThat(player.effect(SPEED).durationTicks()).isEqualTo(40);
  }

  private PlayerSwapHandItemsEvent swap() {
    player.setSneaking(true);
    PlayerSwapHandItemsEvent swap = adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(),
        player.inventory().offHand()));
    player.setSneaking(false);
    return swap;
  }
}
