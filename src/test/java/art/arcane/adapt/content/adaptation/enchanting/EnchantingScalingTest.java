package art.arcane.adapt.content.adaptation.enchanting;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnchantingScalingTest {
  @Test
  void tomeLossChanceReachesZeroAtMaxLevelAndImprovesWithLevels() {
    assertThat(EnchantingTomeRebinding.tomeLossChance(0.9D, 1.0D, 1.0D)).isEqualTo(0.0D);
    assertThat(EnchantingTomeRebinding.tomeLossChance(0.9D, 1.0D, 0.2D)).isCloseTo(0.7D, offset(1.0E-9D));
    assertThat(EnchantingTomeRebinding.tomeLossChance(0.9D, 1.0D, 0.2D))
        .isGreaterThan(EnchantingTomeRebinding.tomeLossChance(0.9D, 1.0D, 0.8D));
  }

  @Test
  void tomeXpCostFloorsAtMinimum() {
    assertThat(EnchantingTomeRebinding.tomeXpCost(5, 3, 2, 0.0D)).isEqualTo(5);
    assertThat(EnchantingTomeRebinding.tomeXpCost(5, 3, 2, 1.0D)).isEqualTo(2);
  }

  @Test
  void tomeRebindingUsesTheShiftRightInventoryGestureInsteadOfPlayerSneakState() {
    assertThat(EnchantingTomeRebinding.isRebindingClick(0, ClickType.SHIFT_RIGHT, true, false)).isTrue();
    assertThat(EnchantingTomeRebinding.isRebindingClick(0, ClickType.RIGHT, true, false)).isFalse();
    assertThat(EnchantingTomeRebinding.isRebindingClick(1, ClickType.SHIFT_RIGHT, true, false)).isFalse();
    assertThat(EnchantingTomeRebinding.isRebindingClick(0, ClickType.SHIFT_RIGHT, true, true)).isFalse();
    assertThat(EnchantingTomeRebinding.isRebindingClick(0, ClickType.SHIFT_RIGHT, false, false)).isFalse();
  }

  @Test
  void tomeRebindingSpendsVanillaLevelsWithoutDesynchronizingExperience() {
    Player player = mock(Player.class);
    when(player.getLevel()).thenReturn(5);

    assertThat(EnchantingTomeRebinding.spendLevels(player, 3)).isTrue();
    verify(player).giveExpLevels(-3);
    assertThat(EnchantingTomeRebinding.spendLevels(player, 6)).isFalse();
    assertThat(EnchantingTomeRebinding.spendLevels(player, 0)).isFalse();
    assertThat(EnchantingTomeRebinding.spendLevels(null, 1)).isFalse();
    verify(player, never()).setLevel(anyInt());
  }

  @Test
  void lapisReturnRunsOnlyAfterACommittedEnchant() throws Exception {
    Method handler = EnchantingLapisReturn.class.getDeclaredMethod("on", EnchantItemEvent.class);
    EventHandler annotation = handler.getAnnotation(EventHandler.class);

    assertThat(annotation.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(annotation.ignoreCancelled()).isTrue();
  }

  @Test
  void quickEnchantNormalizesInvalidBonusDivisors() {
    assertThat(EnchantingQuickEnchant.totalLevelCount(5, 5, 0)).isEqualTo(5);
    assertThat(EnchantingQuickEnchant.totalLevelCount(10, 5, 0)).isEqualTo(20);
    assertThat(EnchantingQuickEnchant.totalLevelCount(10, 5, -4)).isEqualTo(20);
    assertThat(EnchantingQuickEnchant.totalLevelCount(10, 5, 5)).isEqualTo(12);
  }

  @Test
  void soulLinkSaveCostAndCooldownFloorAndDropWithLevels() {
    assertThat(EnchantingSoulLink.soulSaveCost(8, 5, 2, 0.0D)).isEqualTo(8);
    assertThat(EnchantingSoulLink.soulSaveCost(8, 5, 2, 1.0D)).isEqualTo(3);
    assertThat(EnchantingSoulLink.soulRemarkCooldownMs(60000, 45000, 8000, 0.0D)).isEqualTo(60000L);
    assertThat(EnchantingSoulLink.soulRemarkCooldownMs(60000, 45000, 8000, 1.0D)).isEqualTo(15000L);
    assertThat(EnchantingSoulLink.soulRemarkCooldownMs(60000, 45000, 8000, 1.0D))
        .isLessThan(EnchantingSoulLink.soulRemarkCooldownMs(60000, 45000, 8000, 0.0D));
  }

  @Test
  void siphonDropChanceCapsAndQualityBonusScales() {
    assertThat(EnchantingArcaneSiphon.siphonDropChance(0.12D, 0.4D, 0.5D, 0.0D)).isCloseTo(0.12D, offset(1.0E-9D));
    assertThat(EnchantingArcaneSiphon.siphonDropChance(0.12D, 0.4D, 0.5D, 1.0D)).isEqualTo(0.5D);
    assertThat(EnchantingArcaneSiphon.siphonQualityBonus(2, 0.0D)).isZero();
    assertThat(EnchantingArcaneSiphon.siphonQualityBonus(2, 1.0D)).isEqualTo(2);
  }

  @Test
  void runeSightRevealDepthGrowsFromOneToMax() {
    assertThat(EnchantingRuneSight.revealDepth(3, 0.0D)).isEqualTo(1);
    assertThat(EnchantingRuneSight.revealDepth(3, 0.5D)).isEqualTo(2);
    assertThat(EnchantingRuneSight.revealDepth(3, 1.0D)).isEqualTo(3);
    assertThat(EnchantingRuneSight.revealDepth(1, 1.0D)).isEqualTo(1);
  }

  @Test
  void infusionSourceSurvivalCapsAndCostFloors() {
    assertThat(EnchantingInfusionTransfer.infusionSourceSurvival(0.1D, 0.9D, 1.0D, 0.0D)).isCloseTo(0.1D, offset(1.0E-9D));
    assertThat(EnchantingInfusionTransfer.infusionSourceSurvival(0.1D, 0.9D, 1.0D, 1.0D)).isEqualTo(1.0D);
    assertThat(EnchantingInfusionTransfer.infusionXpCost(6, 3, 2, 0.0D)).isEqualTo(6);
    assertThat(EnchantingInfusionTransfer.infusionXpCost(6, 3, 2, 1.0D)).isEqualTo(3);
  }

  @Test
  void echoChargeRateIncreasesWithLevels() {
    assertThat(EnchantingEchoOfKnowledge.echoChargeRate(1, 3, 0.0D)).isEqualTo(1.0D);
    assertThat(EnchantingEchoOfKnowledge.echoChargeRate(1, 3, 1.0D)).isEqualTo(4.0D);
    assertThat(EnchantingEchoOfKnowledge.echoChargeRate(1, 3, 0.5D))
        .isGreaterThan(EnchantingEchoOfKnowledge.echoChargeRate(1, 3, 0.0D));
  }

  @Test
  void configDefaultsAreEnabledWithSaneNonZeroValues() {
    assertThat(new EnchantingCurseCleansing.Config().enabled).isTrue();
    assertThat(new EnchantingCurseCleansing.Config().skillXpPerCurse).isGreaterThan(0);
    assertThat(new EnchantingCurseCleansing.Config().maxLevel).isGreaterThan(0);

    assertThat(new EnchantingTomeRebinding.Config().lossChanceBase).isGreaterThan(0);
    assertThat(new EnchantingTomeRebinding.Config().maxLevel).isGreaterThan(0);

    assertThat(new EnchantingSoulLink.Config().saveCostBase).isGreaterThan(0);
    assertThat(new EnchantingSoulLink.Config().minRemarkCooldown).isGreaterThan(0);

    assertThat(new EnchantingArcaneSiphon.Config().dropChanceBase).isGreaterThan(0);
    assertThat(new EnchantingArcaneSiphon.Config().maxDropChance).isGreaterThan(0);

    assertThat(new EnchantingRuneSight.Config().maxRevealDepth).isGreaterThanOrEqualTo(1);
    assertThat(new EnchantingRuneSight.Config().revealThrottleMs).isGreaterThan(0);

    assertThat(new EnchantingInfusionTransfer.Config().survivalFactor).isGreaterThan(0);
    assertThat(new EnchantingInfusionTransfer.Config().maxSurvival).isGreaterThan(0);

    assertThat(new EnchantingEchoOfKnowledge.Config().chargeRateBase).isGreaterThan(0);
    assertThat(new EnchantingEchoOfKnowledge.Config().chargeThreshold).isGreaterThan(0);
  }
}
