package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemSettings;
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
}
