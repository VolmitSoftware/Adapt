package art.arcane.adapt.content.adaptation.enchanting;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class EnchantingVanillaXpTuningTest {
  @Test
  void xpReturnUsesLinearCappedDefaults() {
    EnchantingXPReturn.Config config = new EnchantingXPReturn.Config();

    assertThat(EnchantingXPReturn.rewardXp(1, config.vanillaXpAtLevelOne, config.vanillaXpPerAdditionalLevel, config.maximumXpPerEnchant)).isEqualTo(2);
    assertThat(EnchantingXPReturn.rewardXp(7, config.vanillaXpAtLevelOne, config.vanillaXpPerAdditionalLevel, config.maximumXpPerEnchant)).isEqualTo(26);
    assertThat(EnchantingXPReturn.rewardXp(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 32)).isEqualTo(32);
    assertThat(config.cooldownMillis).isEqualTo(30000L);
  }

  @Test
  void xpReturnRunsOnlyAfterCommittedEnchant() throws Exception {
    Method handler = EnchantingXPReturn.class.getDeclaredMethod("on", EnchantItemEvent.class);
    EventHandler annotation = handler.getAnnotation(EventHandler.class);

    assertThat(annotation.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(annotation.ignoreCancelled()).isTrue();
  }

  @Test
  void grindstoneRecoveryUsesConservativeEndpoints() {
    EnchantingGrindstoneRecovery.Config config = new EnchantingGrindstoneRecovery.Config();

    assertThat(EnchantingGrindstoneRecovery.recoverChance(0.2D, config.recoverChanceBase, config.recoverChanceFactor, config.maxRecoverChance))
        .isCloseTo(0.15D, offset(1.0E-9D));
    assertThat(EnchantingGrindstoneRecovery.recoverChance(1D, config.recoverChanceBase, config.recoverChanceFactor, config.maxRecoverChance))
        .isEqualTo(0.35D);
    assertThat(Math.round(EnchantingGrindstoneRecovery.bonusXp(0.2D, config.bonusXpBase, config.bonusXpFactor, config.maximumBonusXp))).isEqualTo(2L);
    assertThat(Math.round(EnchantingGrindstoneRecovery.bonusXp(1D, config.bonusXpBase, config.bonusXpFactor, config.maximumBonusXp))).isEqualTo(5L);
    assertThat(EnchantingGrindstoneRecovery.cooldownTicks(0.2D, config.cooldownTicksBase, config.cooldownTicksFactor, config.minimumCooldownTicks)).isEqualTo(184);
    assertThat(EnchantingGrindstoneRecovery.cooldownTicks(1D, config.cooldownTicksBase, config.cooldownTicksFactor, config.minimumCooldownTicks)).isEqualTo(120);
    assertThat(config.skillXpOnRecovery).isEqualTo(8D);
  }

  @Test
  void grindstoneConfigRejectsNonFiniteAndOversizedValues() {
    EnchantingGrindstoneRecovery.Config config = new EnchantingGrindstoneRecovery.Config();
    config.recoverChanceBase = Double.NaN;
    config.recoverChanceFactor = Double.POSITIVE_INFINITY;
    config.maxRecoverChance = 4D;
    config.maximumBonusXp = Double.MAX_VALUE;
    config.minimumCooldownTicks = Integer.MAX_VALUE;

    config.normalizeForPersistence();

    assertThat(config.recoverChanceBase).isZero();
    assertThat(config.recoverChanceFactor).isZero();
    assertThat(config.maxRecoverChance).isEqualTo(1D);
    assertThat(config.maximumBonusXp).isEqualTo(100000D);
    assertThat(config.minimumCooldownTicks).isEqualTo(72000);
  }
}
