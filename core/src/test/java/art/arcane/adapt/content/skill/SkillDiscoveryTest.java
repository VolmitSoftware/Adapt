package art.arcane.adapt.content.skill;

import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillDiscoveryTest {
  @Test
  void emptyServersProduceNoTargetChecks() {
    assertThat(SkillDiscovery.targetCheckBatchSize(0, 64)).isZero();
  }

  @Test
  void lowPopulationPassesRetainEveryPlayer() {
    assertThat(SkillDiscovery.targetCheckBatchSize(32, 64)).isEqualTo(32);
  }

  @Test
  void largePopulationPassesRespectTheConfiguredCeiling() {
    assertThat(SkillDiscovery.targetCheckBatchSize(1000, 64)).isEqualTo(64);
  }

  @Test
  void invalidConfiguredLimitsStillMakeProgress() {
    assertThat(SkillDiscovery.targetCheckBatchSize(1000, 0)).isEqualTo(1);
  }

  @Test
  void advancementXpFollowsTheFrame() {
    assertThat(SkillDiscovery.advancementXp(Id.minecraft("story/mine_stone"), AdvancementFrame.TASK, 150D, 400D, 1000D))
        .isEqualTo(150D);
    assertThat(SkillDiscovery.advancementXp(Id.of("somemod", "goals/first"), AdvancementFrame.GOAL, 150D, 400D, 1000D))
        .isEqualTo(400D);
    assertThat(SkillDiscovery.advancementXp(Id.minecraft("adventure/adventuring_time"), AdvancementFrame.CHALLENGE,
        150D, 400D, 1000D)).isEqualTo(1000D);
  }

  @Test
  void adaptRecipeAndHiddenAdvancementsPayNothing() {
    assertThat(SkillDiscovery.advancementXp(Id.adapt("skill_axes"), AdvancementFrame.TASK, 150D, 400D, 1000D)).isZero();
    assertThat(SkillDiscovery.advancementXp(Id.minecraft("recipes/misc/torch"), AdvancementFrame.TASK, 150D, 400D, 1000D))
        .isZero();
    assertThat(SkillDiscovery.advancementXp(Id.minecraft("story/root"), null, 150D, 400D, 1000D)).isZero();
    assertThat(SkillDiscovery.advancementXp(Id.minecraft("story/root"), AdvancementFrame.TASK, -5D, 400D, 1000D)).isZero();
  }
}
