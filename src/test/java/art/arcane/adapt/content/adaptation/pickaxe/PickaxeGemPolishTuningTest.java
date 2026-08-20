package art.arcane.adapt.content.adaptation.pickaxe;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PickaxeGemPolishTuningTest {
  @Test
  void onlyHeadsSkullsAndDragonEggQualify() {
    assertThat(PickaxeGemPolish.isEligibleTrophy(Material.PLAYER_HEAD, true, true)).isTrue();
    assertThat(PickaxeGemPolish.isEligibleTrophy(Material.WITHER_SKELETON_WALL_SKULL, true, true)).isTrue();
    assertThat(PickaxeGemPolish.isEligibleTrophy(Material.DRAGON_HEAD, true, true)).isTrue();
    assertThat(PickaxeGemPolish.isEligibleTrophy(Material.DRAGON_EGG, true, true)).isTrue();
    assertThat(PickaxeGemPolish.isEligibleTrophy(Material.DIAMOND_ORE, true, true)).isFalse();
    assertThat(PickaxeGemPolish.isEligibleTrophy(Material.DEEPSLATE_EMERALD_ORE, true, true)).isFalse();
    assertThat(PickaxeGemPolish.isEligibleTrophy(Material.AMETHYST_CLUSTER, true, true)).isFalse();
    assertThat(PickaxeGemPolish.isEligibleTrophy(Material.PISTON_HEAD, true, true)).isFalse();
  }

  @Test
  void eligibilityTogglesRemainIndependent() {
    assertThat(PickaxeGemPolish.isEligibleTrophy(Material.CREEPER_HEAD, false, true)).isFalse();
    assertThat(PickaxeGemPolish.isEligibleTrophy(Material.DRAGON_EGG, false, true)).isTrue();
    assertThat(PickaxeGemPolish.isEligibleTrophy(Material.CREEPER_HEAD, true, false)).isTrue();
    assertThat(PickaxeGemPolish.isEligibleTrophy(Material.DRAGON_EGG, true, false)).isFalse();
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
    EventHandler annotation = handler.getAnnotation(EventHandler.class);

    assertThat(annotation.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(annotation.ignoreCancelled()).isTrue();
  }

  @Test
  void hiddenOreBridgeNoLongerReferencesGemPolish() throws Exception {
    String source = Files.readString(Path.of("src/main/java/art/arcane/adapt/content/integration/hiddenore/HiddenOreBridge.java"));

    assertThat(source).doesNotContain("PickaxeGemPolish", "applyGemPolish", "gems-polished");
  }
}
