package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemMastery;
import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.GolemRuneType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuneLoreTest {
  @Test
  void configuredReachUsesTheSameMasterworkStrengthAsRuntime() {
    GolemRunicAugments.Config config = new GolemRunicAugments.Config();
    config.tideRadiusByTier = List.of(3D, 7D, 11D);
    List<String> lore = RuneLore.describe(config, new GolemRune(GolemRuneType.TIDE, 3), new GolemMastery.Perks(0, false, true, 0.25D));

    assertThat(lore).contains("Work and mob detection reach: +13.75 blocks", "Masterwork: tier 3 numeric effects, strength ×1.25");
  }

  @Test
  void numericTierBoostDoesNotPromiseWardsFromUnboostedFlagTables() {
    List<String> lore = RuneLore.describe(new GolemRunicAugments.Config(), new GolemRune(GolemRuneType.EMBER, 1),
        new GolemMastery.Perks(0, false, true, 0.25D));

    assertThat(lore).contains("Attacks ignite targets for 4 seconds");
    assertThat(lore).doesNotContain("Immune to fire damage", "Immune to lava damage");
  }

  @Test
  void boundedEffectsShowActualClampedValues() {
    GolemRunicAugments.Config config = new GolemRunicAugments.Config();
    config.orderChannelsByTier = List.of(40, 40, 40);
    config.orderReclaimDelayByTier = List.of(2D, 2D, 2D);
    List<String> lore = RuneLore.describe(config, new GolemRune(GolemRuneType.ORDER, 1), GolemMastery.Perks.NONE);

    assertThat(lore).containsExactly("Select up to 16 channels", "Failed-job retry delay: 100% shorter");
  }

  @Test
  void everyPurchasedRunicRankAddsSlotsOrTierAccess() {
    GolemRunicAugments.Config config = new GolemRunicAugments.Config();
    for (int rank = 1; rank < config.maxTierByLevel.size(); rank++) {
      boolean moreSlots = config.extraSlotsByLevel.get(rank) > config.extraSlotsByLevel.get(rank - 1);
      boolean higherTier = config.maxTierByLevel.get(rank) > config.maxTierByLevel.get(rank - 1);
      assertThat(moreSlots || higherTier).as("Runic rank %s", rank + 1).isTrue();
    }
    assertThat(config.extraSlotsByLevel.getLast()).isEqualTo(2);
    assertThat(config.maxTierByLevel.getLast()).isEqualTo(3);
  }
}
