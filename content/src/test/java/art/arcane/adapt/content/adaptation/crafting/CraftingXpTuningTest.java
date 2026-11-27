package art.arcane.adapt.content.adaptation.crafting;

import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class CraftingXpTuningTest {
  @Test
  void defaultRewardIsLinearAndBounded() {
    CraftingXP.Config config = new CraftingXP.Config();

    assertThat(CraftingXP.rewardXp(1, config.vanillaXpAtLevelOne, config.vanillaXpPerAdditionalLevel, config.maximumXpPerCraft)).isEqualTo(1);
    assertThat(CraftingXP.rewardXp(7, config.vanillaXpAtLevelOne, config.vanillaXpPerAdditionalLevel, config.maximumXpPerCraft)).isEqualTo(7);
    assertThat(CraftingXP.rewardXp(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 16)).isEqualTo(16);
    assertThat(config.cooldownMillis).isEqualTo(30000L);
  }

  @Test
  void invalidRewardConfigNormalizesSafely() {
    CraftingXP.Config config = new CraftingXP.Config();
    config.vanillaXpAtLevelOne = -1;
    config.vanillaXpPerAdditionalLevel = Integer.MAX_VALUE;
    config.maximumXpPerCraft = -5;
    config.cooldownMillis = Long.MAX_VALUE;

    config.normalizeForPersistence();

    assertThat(config.vanillaXpAtLevelOne).isZero();
    assertThat(config.vanillaXpPerAdditionalLevel).isEqualTo(100000);
    assertThat(config.maximumXpPerCraft).isZero();
    assertThat(config.cooldownMillis).isEqualTo(3600000L);
  }

  @Test
  void rewardRunsOnlyAfterCommittedCraft() throws Exception {
    Method handler = CraftingXP.class.getDeclaredMethod("on", CraftItemEvent.class);
    Handle annotation = handler.getAnnotation(Handle.class);

    assertThat(annotation.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(annotation.ignoreCancelled()).isTrue();
  }
}
