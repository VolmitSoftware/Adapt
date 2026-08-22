package art.arcane.adapt.content.adaptation.sword;

import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwordsHeirloomEdgeTest {
  @Test
  void heirloomEdgeHasNoPerHitDamageHandler() {
    assertThatThrownBy(() -> SwordsHeirloomEdge.class.getDeclaredMethod("on", EntityDamageByEntityEvent.class))
        .isInstanceOf(NoSuchMethodException.class);
  }

  @Test
  void growthPerBankGrowsWithLevel() {
    SwordsHeirloomEdge.Config config = new SwordsHeirloomEdge.Config();
    double growthAtLevelOne = config.growthBase + (levelPercent(1, config.maxLevel) * config.growthFactor);
    double growthAtMaxLevel = config.growthBase + (levelPercent(config.maxLevel, config.maxLevel) * config.growthFactor);
    assertThat(growthAtLevelOne).isGreaterThan(0D);
    assertThat(growthAtMaxLevel).isGreaterThan(growthAtLevelOne);
  }

  @Test
  void bankedBonusNeverExceedsCap() {
    SwordsHeirloomEdge.Config config = new SwordsHeirloomEdge.Config();
    double cap = config.capBase + (levelPercent(config.maxLevel, config.maxLevel) * config.capFactor);
    double growth = config.growthBase + (levelPercent(config.maxLevel, config.maxLevel) * config.growthFactor);
    double bonus = 0D;
    for (int i = 0; i < 1000; i++) {
      bonus = Math.min(cap, bonus + growth);
    }
    assertThat(bonus).isEqualTo(cap);
  }

  private static double levelPercent(int level, int maxLevel) {
    return Math.min(Math.max(0D, (double) level / maxLevel), 1D);
  }
}
