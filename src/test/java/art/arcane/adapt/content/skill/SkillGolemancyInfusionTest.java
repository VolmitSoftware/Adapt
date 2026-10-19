package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.golem.plinth.InfusionRecipe;
import art.arcane.adapt.api.golem.plinth.PlinthStructure;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillGolemancyInfusionTest {
  private static final SkillGolemancy.Config DEFAULTS = new SkillGolemancy.Config();

  @Test
  void infusionDefaultsFollowThePlan() {
    assertThat(DEFAULTS.infuseRuneTier1Xp).isEqualTo(60D);
    assertThat(DEFAULTS.infuseRuneTier2Xp).isEqualTo(180D);
    assertThat(DEFAULTS.infuseRuneTier3Xp).isEqualTo(450D);
    assertThat(DEFAULTS.infuseMasterworkHeartXp).isEqualTo(1500D);
    assertThat(DEFAULTS.challengeInfusionsReward).isEqualTo(500D);
    assertThat(DEFAULTS.pedestalBlocks).containsExactlyElementsOf(PlinthStructure.DEFAULT_PEDESTALS);
    assertThat(DEFAULTS.infusionRecipes).usingRecursiveComparison().isEqualTo(InfusionRecipe.defaults());
  }

  @ParameterizedTest
  @CsvSource({
      "golemancy:rune/swiftness/1, 60",
      "golemancy:rune/bulwark/2, 180",
      "golemancy:rune/tide/3, 450",
      "golemancy:masterwork_heart, 1500",
      "golemancy:rune/tide/4, 0",
      "golemancy:crook, 0"
  })
  void infusionXpFollowsTheResult(String result, double expected) {
    assertThat(SkillGolemancy.infusionXp(DEFAULTS, NamespacedKey.fromString(result))).isEqualTo(expected);
  }

  @Test
  void infusionXpFollowsTheConfig() {
    SkillGolemancy.Config config = new SkillGolemancy.Config();
    config.infuseRuneTier1Xp = 1D;
    config.infuseRuneTier2Xp = 2D;
    config.infuseRuneTier3Xp = 3D;
    config.infuseMasterworkHeartXp = 4D;

    assertThat(SkillGolemancy.infusionXp(config, NamespacedKey.fromString("golemancy:rune/order/1"))).isEqualTo(1D);
    assertThat(SkillGolemancy.infusionXp(config, NamespacedKey.fromString("golemancy:rune/order/2"))).isEqualTo(2D);
    assertThat(SkillGolemancy.infusionXp(config, NamespacedKey.fromString("golemancy:rune/order/3"))).isEqualTo(3D);
    assertThat(SkillGolemancy.infusionXp(config, NamespacedKey.fromString("golemancy:masterwork_heart"))).isEqualTo(4D);
  }

  @Test
  void theRewardKeyNamesTheInfusedItem() {
    assertThat(SkillGolemancy.infusionRewardKey(NamespacedKey.fromString("golemancy:rune/ember/2"))).isEqualTo("golemancy:craft:rune/ember/2");
    assertThat(SkillGolemancy.infusionRewardKey(NamespacedKey.fromString("golemancy:masterwork_heart"))).isEqualTo("golemancy:craft:masterwork_heart");
  }
}
