package art.arcane.adapt.content.adaptation.pickaxe;

import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class PickaxeGemPolishTuningTest {
  private static Id id(String path) {
    return Id.minecraft(path);
  }

  @Test
  void onlyHeadsSkullsAndDragonEggQualify() {
    assertThat(PickaxeGemPolish.isEligibleTrophy(id("player_head"), true, true)).isTrue();
    assertThat(PickaxeGemPolish.isEligibleTrophy(id("wither_skeleton_wall_skull"), true, true)).isTrue();
    assertThat(PickaxeGemPolish.isEligibleTrophy(id("dragon_head"), true, true)).isTrue();
    assertThat(PickaxeGemPolish.isEligibleTrophy(id("dragon_egg"), true, true)).isTrue();
    assertThat(PickaxeGemPolish.isEligibleTrophy(id("diamond_ore"), true, true)).isFalse();
    assertThat(PickaxeGemPolish.isEligibleTrophy(id("deepslate_emerald_ore"), true, true)).isFalse();
    assertThat(PickaxeGemPolish.isEligibleTrophy(id("amethyst_cluster"), true, true)).isFalse();
    assertThat(PickaxeGemPolish.isEligibleTrophy(id("piston_head"), true, true)).isFalse();
  }

  @Test
  void eligibilityTogglesRemainIndependent() {
    assertThat(PickaxeGemPolish.isEligibleTrophy(id("creeper_head"), false, true)).isFalse();
    assertThat(PickaxeGemPolish.isEligibleTrophy(id("dragon_egg"), false, true)).isTrue();
    assertThat(PickaxeGemPolish.isEligibleTrophy(id("creeper_head"), true, false)).isTrue();
    assertThat(PickaxeGemPolish.isEligibleTrophy(id("dragon_egg"), true, false)).isFalse();
  }

  @Test
  void defaultTrophyRewardIsLinearAndCapped() {
    PickaxeGemPolish.Config config = new PickaxeGemPolish.Config();

    assertThat(PickaxeGemPolish.rewardXp(1, config.vanillaXpAtLevelOne, config.vanillaXpPerAdditionalLevel, config.maximumXpPerTrophy)).isEqualTo(7);
    assertThat(PickaxeGemPolish.rewardXp(5, config.vanillaXpAtLevelOne, config.vanillaXpPerAdditionalLevel, config.maximumXpPerTrophy)).isEqualTo(19);
    assertThat(PickaxeGemPolish.rewardXp(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 24)).isEqualTo(24);
    assertThat(config.rejectPlayerModifiedBlocks).isTrue();
  }

  @Test
  void rewardRunsOnlyAfterCommittedBreak() throws Exception {
    Method handler = PickaxeGemPolish.class.getDeclaredMethod("on", BlockBreakEvent.class);
    Handle annotation = handler.getAnnotation(Handle.class);

    assertThat(annotation.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(annotation.ignoreCancelled()).isTrue();
  }
}
