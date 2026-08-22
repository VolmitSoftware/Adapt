package art.arcane.adapt.content.adaptation.enchanting;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

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
  void tomeRebindingRecognizesEveryAnvilState() throws Exception {
    assertThat(EnchantingTomeRebinding.isAnvilTarget(Material.ANVIL)).isTrue();
    assertThat(EnchantingTomeRebinding.isAnvilTarget(Material.CHIPPED_ANVIL)).isTrue();
    assertThat(EnchantingTomeRebinding.isAnvilTarget(Material.DAMAGED_ANVIL)).isTrue();
    assertThat(EnchantingTomeRebinding.isAnvilTarget(Material.ENCHANTING_TABLE)).isFalse();

    Method handler = EnchantingTomeRebinding.class.getDeclaredMethod("on", PlayerDropItemEvent.class);
    EventHandler annotation = handler.getAnnotation(EventHandler.class);
    assertThat(annotation.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(annotation.ignoreCancelled()).isTrue();
  }

  @Test
  void tomeRebindingDoesNotConsumeStacksAndPreservesStoredLevels() {
    assertThat(EnchantingTomeRebinding.isSplittableBook(Material.ENCHANTED_BOOK, 1, 2)).isTrue();
    assertThat(EnchantingTomeRebinding.isSplittableBook(Material.ENCHANTED_BOOK, 2, 2)).isFalse();
    assertThat(EnchantingTomeRebinding.isSplittableBook(Material.BOOK, 1, 2)).isFalse();
    assertThat(EnchantingTomeRebinding.isSplittableBook(Material.ENCHANTED_BOOK, 1, 1)).isFalse();
    assertThat(EnchantingTomeRebinding.normalizedStoredLevel(10)).isEqualTo(10);
    assertThat(EnchantingTomeRebinding.normalizedStoredLevel(0)).isEqualTo(1);
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
  void arcaneSiphonAcceptsPlayersOnlyAtMaximumLevel() {
    assertThat(EnchantingArcaneSiphon.isEligibleVictim(false, 1, 5)).isTrue();
    assertThat(EnchantingArcaneSiphon.isEligibleVictim(true, 4, 5)).isFalse();
    assertThat(EnchantingArcaneSiphon.isEligibleVictim(true, 5, 5)).isTrue();
    assertThat(EnchantingArcaneSiphon.isEligibleVictim(true, 6, 5)).isTrue();
    assertThat(EnchantingArcaneSiphon.isEligibleVictim(false, 0, 5)).isFalse();
  }

  @Test
  void arcaneSiphonPublishesItsBookBeforeDropRoutingHandlers() throws Exception {
    Method handler = EnchantingArcaneSiphon.class.getDeclaredMethod("on", EntityDeathEvent.class);
    EventHandler annotation = handler.getAnnotation(EventHandler.class);

    assertThat(annotation.priority()).isEqualTo(EventPriority.HIGH);
    assertThat(annotation.ignoreCancelled()).isTrue();
  }

  @Test
  void arcaneSiphonScansEveryEquipmentSlot() {
    EntityEquipment equipment = mock(EntityEquipment.class);
    ItemStack helmet = enchantedItem(Material.DIAMOND_HELMET);
    ItemStack chestplate = enchantedItem(Material.DIAMOND_CHESTPLATE);
    ItemStack leggings = enchantedItem(Material.DIAMOND_LEGGINGS);
    ItemStack boots = enchantedItem(Material.DIAMOND_BOOTS);
    ItemStack mainHand = enchantedItem(Material.DIAMOND_SWORD);
    ItemStack offHand = enchantedItem(Material.SHIELD);
    when(equipment.getHelmet()).thenReturn(helmet);
    when(equipment.getChestplate()).thenReturn(chestplate);
    when(equipment.getLeggings()).thenReturn(leggings);
    when(equipment.getBoots()).thenReturn(boots);
    when(equipment.getItemInMainHand()).thenReturn(mainHand);
    when(equipment.getItemInOffHand()).thenReturn(offHand);

    Map<Enchantment, Integer> enchants = EnchantingArcaneSiphon.collectGearEnchants(equipment);

    assertThat(enchants).isEmpty();
    verify(equipment).getHelmet();
    verify(equipment).getChestplate();
    verify(equipment).getLeggings();
    verify(equipment).getBoots();
    verify(equipment).getItemInMainHand();
    verify(equipment).getItemInOffHand();
  }

  @Test
  void arcaneSiphonKeepsTheHighestLevelOfDuplicateEnchantments() {
    Map<String, Integer> enchants = new HashMap<>();
    enchants.put("shared", 1);

    EnchantingArcaneSiphon.mergeHighest(enchants, Map.of("shared", 3, "offhand-only", 1));
    EnchantingArcaneSiphon.mergeHighest(enchants, Map.of("shared", 2));

    assertThat(enchants).containsEntry("shared", 3).containsEntry("offhand-only", 1).hasSize(2);
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

  private ItemStack enchantedItem(Material material) {
    ItemStack item = mock(ItemStack.class);
    when(item.getType()).thenReturn(material);
    when(item.getAmount()).thenReturn(1);
    when(item.getEnchantments()).thenReturn(Map.of());
    return item;
  }
}
