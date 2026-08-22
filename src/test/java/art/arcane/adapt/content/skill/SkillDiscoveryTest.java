package art.arcane.adapt.content.skill;

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
}
