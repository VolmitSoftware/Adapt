package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemBrainSettings;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemSettings;
import art.arcane.adapt.api.golem.GolemWorkSettings;
import art.arcane.adapt.api.golem.node.NodeSettings;
import art.arcane.adapt.api.golem.node.NodeVisibility;
import art.arcane.adapt.api.golem.reveal.RevealSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillGolemancySettingsTest {
  @Test
  void configDefaultsProduceTheRuntimeDefaults() {
    assertThat(SkillGolemancy.golemSettings(new SkillGolemancy.Config())).isEqualTo(GolemSettings.defaults());
  }

  @Test
  void configValuesReachTheRuntimeSettings() {
    SkillGolemancy.Config config = new SkillGolemancy.Config();
    config.dormantInvulnerable = false;
    config.guardWhileOwnerOffline = true;
    config.serverMaxActiveGolems = 12;
    config.maxBoundGolemsPerChunk = 3;
    config.hardCapPerPlayer = 5;
    config.bodyScaleFrost = 0.6D;
    config.bodyScaleCopper = 0.9D;
    config.bodyScaleIron = 0.4D;
    config.bodyScaleBone = 0.49D;
    config.allowedCoresFrost = new ArrayList<>(List.of("guardian"));
    config.allowedCoresCopper = new ArrayList<>(List.of("SORTER", "nonsense"));
    config.allowedCoresIron = new ArrayList<>();
    config.deathDropCoreChance = 0.25D;
    config.deathDropRuneChance = 0.75D;

    GolemSettings settings = SkillGolemancy.golemSettings(config);

    assertThat(settings.dormantInvulnerable()).isFalse();
    assertThat(settings.guardWhileOwnerOffline()).isTrue();
    assertThat(settings.serverMaxActiveGolems()).isEqualTo(12);
    assertThat(settings.maxBoundGolemsPerChunk()).isEqualTo(3);
    assertThat(settings.hardCapPerPlayer()).isEqualTo(5);
    assertThat(settings.bodyScale(GolemBody.FROST)).isEqualTo(0.6D);
    assertThat(settings.bodyScale(GolemBody.COPPER)).isEqualTo(0.9D);
    assertThat(settings.bodyScale(GolemBody.IRON)).isEqualTo(0.4D);
    assertThat(settings.bodyScale(GolemBody.BONE)).isEqualTo(0.49D);
    assertThat(settings.allowedCores(GolemBody.FROST)).containsExactly(GolemCoreType.GUARDIAN);
    assertThat(settings.allowedCores(GolemBody.COPPER)).containsExactly(GolemCoreType.SORTER);
    assertThat(settings.allowedCores(GolemBody.IRON)).isEmpty();
    assertThat(settings.deathDropCoreChance()).isEqualTo(0.25D);
    assertThat(settings.deathDropRuneChance()).isEqualTo(0.75D);
  }

  @Test
  void configDefaultsProduceTheBrainDefaults() {
    assertThat(SkillGolemancy.brainSettings(new SkillGolemancy.Config())).isEqualTo(GolemBrainSettings.defaults());
    assertThat(GolemBrainSettings.defaults()).isEqualTo(new GolemBrainSettings(5, 32D, 32D, 8, 10));
  }

  @Test
  void brainConfigValuesReachTheBrainSettingsClamped() {
    SkillGolemancy.Config config = new SkillGolemancy.Config();
    config.brainIntervalTicks = 40;
    config.followTeleportDistance = 48D;
    config.leashRadius = 2D;
    config.idleReturnSeconds = 3;
    config.copperRepathTicks = 0;

    GolemBrainSettings settings = SkillGolemancy.brainSettings(config);

    assertThat(settings.brainIntervalTicks()).isEqualTo(20);
    assertThat(settings.followTeleportDistance()).isEqualTo(48D);
    assertThat(settings.leashRadius()).isEqualTo(GolemBrainSettings.MIN_LEASH_RADIUS);
    assertThat(settings.idleReturnSeconds()).isEqualTo(3);
    assertThat(settings.copperRepathTicks()).isEqualTo(2);
    assertThat(SkillGolemancy.brainSettings(withInterval(config, 1)).brainIntervalTicks()).isEqualTo(2);
  }

  @Test
  void revealConfigDefaultsProduceTheRevealDefaults() {
    assertThat(SkillGolemancy.revealSettings(new SkillGolemancy.Config())).isEqualTo(RevealSettings.defaults());
  }

  @Test
  void revealConfigValuesReachTheRevealSettings() {
    SkillGolemancy.Config config = new SkillGolemancy.Config();
    config.revealRange = 20D;
    config.labelRange = 5D;
    config.revealPollTicks = 4;

    RevealSettings settings = SkillGolemancy.revealSettings(config);

    assertThat(settings.revealRange()).isEqualTo(20D);
    assertThat(settings.labelRange()).isEqualTo(5D);
    assertThat(settings.pollTicks()).isEqualTo(4);
  }

  @Test
  void nodeConfigDefaultsProduceTheNodeDefaults() {
    assertThat(SkillGolemancy.nodeSettings(new SkillGolemancy.Config())).isEqualTo(NodeSettings.defaults());
  }

  @Test
  void nodeConfigValuesReachTheNodeSettings() {
    SkillGolemancy.Config config = new SkillGolemancy.Config();
    config.maxNodesPerChunk = 4;
    config.nodeScanIntervalTicks = 20;
    config.blocksPerNodeScan = 128;
    config.maxOpenTasksPerNode = 8;
    config.nodeVisibility = "any_golemancer";

    assertThat(SkillGolemancy.nodeSettings(config)).isEqualTo(new NodeSettings(4, 20, 128, 8, NodeVisibility.ANY_GOLEMANCER));
  }

  @Test
  void workConfigDefaultsProduceTheWorkDefaults() {
    SkillGolemancy.Config config = new SkillGolemancy.Config();

    assertThat(SkillGolemancy.workSettings(config)).isEqualTo(GolemWorkSettings.defaults());
    assertThat(config.workXpHourlyCap).isEqualTo(1200D);
    assertThat(config.workXpOwnerRadius).isEqualTo(96D);
    assertThat(config.challengeTasksReward).isEqualTo(500D);
    assertThat(config.challengeItemsMovedReward).isEqualTo(500D);
  }

  @Test
  void negativeOrBrokenWorkXpValuesAreClampedAtLoad() {
    SkillGolemancy.Config config = new SkillGolemancy.Config();
    config.workXpHourlyCap = -10D;
    config.workXpOwnerRadius = Double.NaN;

    config.bindXpHourlyCap = Double.NEGATIVE_INFINITY;

    SkillGolemancy.clampXpCaps(config);

    assertThat(config.workXpHourlyCap).isZero();
    assertThat(config.workXpOwnerRadius).isZero();
    assertThat(config.bindXpHourlyCap).isZero();

    config.workXpHourlyCap = 300D;
    config.workXpOwnerRadius = 40D;
    config.bindXpHourlyCap = 250D;
    SkillGolemancy.clampXpCaps(config);
    assertThat(config.workXpHourlyCap).isEqualTo(300D);
    assertThat(config.workXpOwnerRadius).isEqualTo(40D);
    assertThat(config.bindXpHourlyCap).isEqualTo(250D);
  }

  @Test
  void workConfigValuesReachTheWorkSettings() {
    SkillGolemancy.Config config = new SkillGolemancy.Config();
    config.taskLeaseSeconds = 30;
    config.baseWorkRadius = 16D;
    config.stuckTimeoutSeconds = 6;
    config.stuckReleasesBeforePause = 5;
    config.stuckPauseSeconds = 40;

    assertThat(SkillGolemancy.workSettings(config)).isEqualTo(new GolemWorkSettings(30, 16D, 6, 5, 40));
  }

  private static SkillGolemancy.Config withInterval(SkillGolemancy.Config config, int ticks) {
    config.brainIntervalTicks = ticks;
    return config;
  }
}
