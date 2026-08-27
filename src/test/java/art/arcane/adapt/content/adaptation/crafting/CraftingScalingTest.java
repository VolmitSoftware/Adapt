package art.arcane.adapt.content.adaptation.crafting;

import art.arcane.adapt.api.EventHandlerInvoker;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.util.config.TomlCodec;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CraftingScalingTest {
  @Test
  void batchCapScalesFromBaseToBasePlusFactor() {
    assertThat(CraftingBulkArtisan.batchCap(32, 96, 0.0)).isEqualTo(32);
    assertThat(CraftingBulkArtisan.batchCap(32, 96, 1.0)).isEqualTo(128);
    assertThat(CraftingBulkArtisan.batchCap(32, 96, 0.5)).isGreaterThan(CraftingBulkArtisan.batchCap(32, 96, 0.0));
  }

  @Test
  void refundChanceIsFeltAtBaseAndClampedToMax() {
    assertThat(CraftingThriftyHands.refundChance(0.15, 0.5, 0.6, 0.0)).isCloseTo(0.15, offset(1.0E-9));
    assertThat(CraftingThriftyHands.refundChance(0.15, 0.5, 0.6, 1.0)).isCloseTo(0.6, offset(1.0E-9));
  }

  @Test
  void masterworkChanceAndBonusScaleUpWithLevel() {
    assertThat(CraftingMasterwork.masterworkChance(0.2, 0.55, 0.75, 0.0)).isCloseTo(0.2, offset(1.0E-9));
    assertThat(CraftingMasterwork.masterworkChance(0.2, 0.55, 0.75, 1.0)).isCloseTo(0.75, offset(1.0E-9));
    assertThat(CraftingMasterwork.bonusPercent(0.1, 0.4, 0.0)).isCloseTo(0.1, offset(1.0E-9));
    assertThat(CraftingMasterwork.bonusPercent(0.1, 0.4, 1.0)).isCloseTo(0.5, offset(1.0E-9));
    assertThat(CraftingMasterwork.bonusDurability(528, 0.5)).isEqualTo(264);
  }

  @Test
  void preserveChanceReachesLosslessAtFullLevel() {
    assertThat(CraftingTinkerer.preserveChance(0.4, 0.6, 0.0)).isCloseTo(0.4, offset(1.0E-9));
    assertThat(CraftingTinkerer.preserveChance(0.4, 0.6, 1.0)).isCloseTo(1.0, offset(1.0E-9));
  }

  @Test
  void provisionerBonusScalesUpWithLevel() {
    assertThat(CraftingProvisioner.bonusChance(0.25, 0.5, 0.75, 0.0)).isCloseTo(0.25, offset(1.0E-9));
    assertThat(CraftingProvisioner.bonusChance(0.25, 0.5, 0.75, 1.0)).isCloseTo(0.75, offset(1.0E-9));
    assertThat(CraftingProvisioner.bonusPortions(1, 2, 0.0)).isEqualTo(1);
    assertThat(CraftingProvisioner.bonusPortions(1, 2, 1.0)).isEqualTo(3);
  }

  @Test
  void provisionerSelectsTheFirstNearestEligiblePlayerInsideTheRadius() {
    World world = mock(World.class);
    Location furnace = new Location(world, 0D, 64D, 0D);
    Player outside = playerAt(world, 9D, 64D, 0D);
    Player inactive = playerAt(world, 1D, 64D, 0D);
    Player firstTie = playerAt(world, 2D, 64D, 0D);
    Player secondTie = playerAt(world, -2D, 64D, 0D);

    Player selected = CraftingProvisioner.nearestEligiblePlayer(
        furnace,
        8D,
        List.of(outside, inactive, firstTie, secondTie),
        player -> player != inactive
    );

    assertThat(selected).isSameAs(firstTie);
  }

  @Test
  void signatureAmplifierIsSlightAtLowLevelAndClampedAtMax() {
    assertThat(CraftingSignature.tradeAmplifier(0, 1, 1, 0.0)).isEqualTo(0);
    assertThat(CraftingSignature.tradeAmplifier(0, 1, 1, 1.0)).isEqualTo(1);
    assertThat(CraftingSignature.tradeAmplifier(0, 1, 1, 2.0)).isEqualTo(1);
  }

  @Test
  void craftingXpFitUsesEmptySlotsAndPartialStackRoom() {
    assertThat(CraftingXP.canFit(1, 64, 1, 0)).isTrue();
    assertThat(CraftingXP.canFit(64, 64, 1, 0)).isTrue();
    assertThat(CraftingXP.canFit(65, 64, 1, 0)).isFalse();
    assertThat(CraftingXP.canFit(64, 64, 0, 63)).isFalse();
    assertThat(CraftingXP.canFit(64, 64, 0, 64)).isTrue();
    assertThat(CraftingXP.canFit(10, 64, 0, 4)).isFalse();
    assertThat(CraftingXP.canFit(10, 64, 0, 10)).isTrue();
    assertThat(CraftingXP.canFit(2, 1, 1, 0)).isFalse();
    assertThat(CraftingXP.canFit(2, 1, 2, 0)).isTrue();
  }

  @Test
  void craftingXpFitToleratesDegenerateInputs() {
    assertThat(CraftingXP.canFit(0, 64, 0, 0)).isTrue();
    assertThat(CraftingXP.canFit(-1, 64, 0, 0)).isTrue();
    assertThat(CraftingXP.canFit(1, 0, 1, 0)).isTrue();
    assertThat(CraftingXP.canFit(1, 64, -3, -5)).isFalse();
  }

  @Test
  void bundleRecipeWrapsAChestInLeather() {
    assertThat(CraftingBackpacks.recipeShape()).isEqualTo(List.of("LLL", "LCL", "LLL"));
  }

  @Test
  void compactorIsOneLevelAndCoversEveryMaterial() {
    assertThat(new CraftingCompactor.Config().maxLevel).isEqualTo(1);
    assertThat(CraftingCompactor.materialsCovered()).isEqualTo(13);
    int glowstoneUnitsPerBlock = CraftingCompactor.unitsPerBlock(Material.GLOWSTONE_DUST);
    assertThat(glowstoneUnitsPerBlock).isEqualTo(4);
    assertThat(CraftingCompactor.blocksFor(64, glowstoneUnitsPerBlock)).isEqualTo(16);
    assertThat(CraftingCompactor.unitsConsumed(16, glowstoneUnitsPerBlock)).isEqualTo(64);

    int coalUnitsPerBlock = CraftingCompactor.unitsPerBlock(Material.COAL);
    assertThat(coalUnitsPerBlock).isEqualTo(9);
    assertThat(CraftingCompactor.blocksFor(64, coalUnitsPerBlock)).isEqualTo(7);
    assertThat(CraftingCompactor.unitsConsumed(7, coalUnitsPerBlock)).isEqualTo(63);
  }

  @Test
  void deconstructionCountsEveryShapedRecipeSlot() {
    String[] chestShape = {"PPP", "P P", "PPP"};
    Map<Material, Integer> ingredientCounts = CraftingDeconstruction.shapedIngredientCounts(
        chestShape,
        Map.of('P', Material.OAK_PLANKS)
    );
    int ingredients = CraftingDeconstruction.shapedIngredientCount(
        chestShape,
        Map.of('P', Material.OAK_PLANKS)
    );

    assertThat(ingredientCounts).containsExactly(Map.entry(Material.OAK_PLANKS, 8));
    assertThat(ingredients).isEqualTo(8);
    assertThat(CraftingDeconstruction.salvageAmount(ingredients, 1, 1)).isEqualTo(4);
    assertThat(CraftingDeconstruction.salvageAmount(ingredients, 64, 1)).isEqualTo(256);
    assertThat(CraftingDeconstruction.splitAmounts(256, 64)).containsExactly(64, 64, 64, 64);
  }

  @Test
  void deconstructionAcceptsOnlyFullyRepairedArmorAndUsesPlainRecipeLookup() {
    ItemStack repairedArmor = mock(ItemStack.class);
    Damageable repairedMeta = mock(Damageable.class);
    when(repairedArmor.getType()).thenReturn(Material.IRON_LEGGINGS);
    when(repairedArmor.getAmount()).thenReturn(1);
    when(repairedArmor.getItemMeta()).thenReturn(repairedMeta);
    when(repairedMeta.getDamage()).thenReturn(0);
    ItemStack recipeLookup = mock(ItemStack.class);
    when(repairedArmor.clone()).thenReturn(recipeLookup);

    ItemStack damagedArmor = mock(ItemStack.class);
    Damageable damagedMeta = mock(Damageable.class);
    when(damagedArmor.getType()).thenReturn(Material.DIAMOND_CHESTPLATE);
    when(damagedArmor.getItemMeta()).thenReturn(damagedMeta);
    when(damagedMeta.getDamage()).thenReturn(1);

    ItemStack ordinaryItem = mock(ItemStack.class);
    when(ordinaryItem.getType()).thenReturn(Material.CHEST);

    assertThat(CraftingDeconstruction.hasEligibleRepairState(repairedArmor)).isTrue();
    assertThat(CraftingDeconstruction.hasEligibleRepairState(damagedArmor)).isFalse();
    assertThat(CraftingDeconstruction.hasEligibleRepairState(ordinaryItem)).isTrue();
    assertThat(CraftingDeconstruction.recipeLookupItem(repairedArmor)).isSameAs(recipeLookup);
    verify(recipeLookup).setItemMeta(null);
    assertThat(CraftingDeconstruction.recipeLookupItem(ordinaryItem)).isSameAs(ordinaryItem);
  }

  @Test
  void tinkererEnchantMergeKeepsHighestLevelFromEitherTool() {
    Map<String, Integer> merged = new HashMap<>();
    CraftingTinkerer.mergeEnchants(merged, Map.of("efficiency", 3));
    CraftingTinkerer.mergeEnchants(merged, Map.of("efficiency", 5, "unbreaking", 2));

    assertThat(merged)
        .containsEntry("efficiency", 5)
        .containsEntry("unbreaking", 2);
  }

  @Test
  void compactorPreservesConfiguredLevelCapWhileCanonicalizing() throws IOException {
    CraftingCompactor.Config config = TomlCodec.fromToml("""
        maxLevel = 4
        maxPlayersPerPass = 64
        baseCost = 3
        """, CraftingCompactor.Config.class);
    String persisted = TomlCodec.toToml(config, "adaptation:crafting-compactor");

    assertThat(config.maxLevel).isEqualTo(4);
    assertThat(persisted).contains("maxLevel = 4");
    assertThat(persisted).doesNotContain("maxPlayersPerPass");
  }

  @Test
  void compactorRequiresItsExactWorldGesture() {
    assertThat(CraftingCompactor.isActivation(true, 1, true, Material.CRAFTING_TABLE)).isTrue();
    assertThat(CraftingCompactor.isActivation(false, 1, true, Material.CRAFTING_TABLE)).isFalse();
    assertThat(CraftingCompactor.isActivation(true, 0, true, Material.CRAFTING_TABLE)).isFalse();
    assertThat(CraftingCompactor.isActivation(true, 1, false, Material.CRAFTING_TABLE)).isFalse();
    assertThat(CraftingCompactor.isActivation(true, 1, true, Material.STONE)).isFalse();
  }

  @Test
  void compactorOwnsItsExactSwapGestureWhenAlreadyCancelled() throws ReflectiveOperationException {
    Method handler = CraftingCompactor.class.getDeclaredMethod("on", PlayerSwapHandItemsEvent.class);
    EventHandler eventHandler = handler.getAnnotation(EventHandler.class);

    assertThat(handler.isAnnotationPresent(ReceiveCancelledEvents.class)).isTrue();
    assertThat(eventHandler.ignoreCancelled()).isFalse();
    assertThat(EventHandlerInvoker.shouldIgnoreCancelled(handler, eventHandler, PlayerSwapHandItemsEvent.class)).isFalse();
  }

  private static Player playerAt(World world, double x, double y, double z) {
    Player player = mock(Player.class);
    when(player.getLocation()).thenReturn(new Location(world, x, y, z));
    return player;
  }
}
