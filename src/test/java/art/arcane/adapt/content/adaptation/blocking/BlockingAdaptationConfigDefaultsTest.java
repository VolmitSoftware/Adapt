package art.arcane.adapt.content.adaptation.blocking;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlockingAdaptationConfigDefaultsTest {
  @Test
  void shieldWallDefaultsAreFeltAtLevelOneAndImproveWithLevel() {
    BlockingShieldWall.Config c = new BlockingShieldWall.Config();
    assertThat(c.rangeBase).isGreaterThan(0);
    assertThat(c.rangeFactor).isGreaterThan(0);
    assertThat(c.arcDegreesBase).isGreaterThan(0);
    assertThat(c.arcDegreesFactor).isGreaterThan(0);
    assertThat(c.damageReductionBase).isBetween(0.0001, c.maxDamageReduction);
    assertThat(c.damageReductionFactor).isGreaterThan(0);
    assertThat(c.maxDamageReduction).isGreaterThan(c.damageReductionBase).isLessThanOrEqualTo(1.0);
    assertThat(c.xpPerDamageShielded).isGreaterThan(0);
  }

  @Test
  void perfectGuardWindowWidensWithLevelAndStaggerIsNonZero() {
    BlockingPerfectGuard.Config c = new BlockingPerfectGuard.Config();
    assertThat(c.windowMillisBase).isGreaterThan(0);
    assertThat(c.windowMillisFactor).isGreaterThan(0);
    assertThat(c.staggerTicksBase).isGreaterThan(0);
    assertThat(c.staggerTicksFactor).isGreaterThan(0);
    assertThat(c.staggerAmplifierFactor).isGreaterThan(0);
    assertThat(c.staggerKnockback).isGreaterThan(0);
    assertThat(c.cooldownMillis).isGreaterThan(0);
    assertThat(c.xpOnNegate).isGreaterThan(0);
  }

  @Test
  void temperedGuardChanceAndAmountAreNonZeroAndClamped() {
    BlockingTemperedGuard.Config c = new BlockingTemperedGuard.Config();
    assertThat(c.repairChanceBase).isBetween(0.0001, c.maxRepairChance);
    assertThat(c.repairChanceFactor).isGreaterThan(0);
    assertThat(c.maxRepairChance).isGreaterThan(c.repairChanceBase).isLessThanOrEqualTo(1.0);
    assertThat(c.repairAmountBase).isGreaterThanOrEqualTo(1);
    assertThat(c.repairAmountFactor).isGreaterThan(0);
    assertThat(c.xpPerDurabilityRepaired).isGreaterThan(0);
  }

  @Test
  void temperedGuardListensForActualShieldDurabilityDamage() throws ReflectiveOperationException {
    Method handler = BlockingTemperedGuard.class.getDeclaredMethod("on", PlayerItemDamageEvent.class);
    EventHandler eventHandler = handler.getAnnotation(EventHandler.class);

    assertThat(eventHandler).isNotNull();
    assertThat(eventHandler.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(eventHandler.ignoreCancelled()).isTrue();
    ItemStack shield = item(Material.SHIELD);
    ItemStack chestplate = item(Material.DIAMOND_CHESTPLATE);
    assertThat(BlockingTemperedGuard.isShieldDamage(shield)).isTrue();
    assertThat(BlockingTemperedGuard.isShieldDamage(chestplate)).isFalse();
    assertThat(BlockingTemperedGuard.isShieldDamage(null)).isFalse();
  }

  @Test
  void resolveRecoveryAndResistanceScaleUpward() {
    BlockingShieldbearersResolve.Config c = new BlockingShieldbearersResolve.Config();
    assertThat(c.recoverySpeedBase).isBetween(0.0001, c.maxRecoverySpeed);
    assertThat(c.recoverySpeedFactor).isGreaterThan(0);
    assertThat(c.maxRecoverySpeed).isGreaterThan(c.recoverySpeedBase).isLessThan(1.0);
    assertThat(c.resistanceAmplifierFactor).isGreaterThan(0);
    assertThat(c.minResistanceTicks).isGreaterThan(0);
    assertThat(c.minCooldownTicks).isGreaterThan(0);
    assertThat(c.reprocessGuardMillis).isGreaterThan(0);
    assertThat(c.xpOnResolve).isGreaterThan(0);
  }

  @Test
  void phalanxCrafterUnlocksTwoRecipeTiers() {
    BlockingPhalanxCrafter.Config c = new BlockingPhalanxCrafter.Config();
    assertThat(c.maxLevel).isEqualTo(2);
    assertThat(c.baseCost).isGreaterThan(0);
    assertThat(c.initialCost).isGreaterThan(0);
  }

  @Test
  void phalanxCrafterIdentifiesPlainAndCustomShieldFaces() {
    assertThat(BlockingPhalanxCrafter.hasVisibleShieldDesign(DyeColor.WHITE, 0)).isFalse();
    assertThat(BlockingPhalanxCrafter.hasVisibleShieldDesign(DyeColor.BLACK, 0)).isTrue();
    assertThat(BlockingPhalanxCrafter.hasVisibleShieldDesign(DyeColor.WHITE, 1)).isTrue();
    assertThat(BlockingPhalanxCrafter.hasVisibleShieldDesign(null, 0)).isFalse();

    ItemStack shield = item(Material.SHIELD);
    ItemStack[] matrix = {
        item(Material.NETHERITE_INGOT),
        shield,
        item(Material.NETHERITE_INGOT)
    };
    assertThat(BlockingPhalanxCrafter.findShield(matrix)).isSameAs(shield);
    assertThat(BlockingPhalanxCrafter.findShield(new ItemStack[]{item(Material.STICK)})).isNull();
    assertThat(BlockingPhalanxCrafter.findShield(null)).isNull();
  }

  @Test
  void phalanxCrafterUsesShieldMetadataForItsCraftedFace() throws Exception {
    String source = Files.readString(Path.of(
        "src/main/java/art/arcane/adapt/content/adaptation/blocking/BlockingPhalanxCrafter.java"));

    assertThat(source)
        .contains("meta instanceof ShieldMeta shieldMeta", "applyDefaultShieldDesign(shieldMeta);")
        .doesNotContain("meta instanceof BlockStateMeta");
  }

  @Test
  void interposeShareAndRangeScaleUpwardWithHungerCost() {
    BlockingInterpose.Config c = new BlockingInterpose.Config();
    assertThat(c.redirectShareBase).isBetween(0.0001, c.maxRedirectShare);
    assertThat(c.redirectShareFactor).isGreaterThan(0);
    assertThat(c.maxRedirectShare).isGreaterThan(c.redirectShareBase).isLessThanOrEqualTo(1.0);
    assertThat(c.rangeBase).isGreaterThan(0);
    assertThat(c.rangeFactor).isGreaterThan(0);
    assertThat(c.lowHealthThreshold).isBetween(0.0001, 1.0);
    assertThat(c.durabilityPerDamage).isGreaterThan(0);
    assertThat(c.exhaustionPerRedirect).isGreaterThan(0);
    assertThat(c.xpPerDamageRedirected).isGreaterThan(0);
  }

  private static ItemStack item(Material material) {
    ItemStack item = mock(ItemStack.class);
    when(item.getType()).thenReturn(material);
    return item;
  }
}
