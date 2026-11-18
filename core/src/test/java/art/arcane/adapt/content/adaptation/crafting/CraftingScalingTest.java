package art.arcane.adapt.content.adaptation.crafting;

import art.arcane.adapt.TestCores;
import art.arcane.adapt.api.ComponentRegistrar;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.HandlerMethod;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeChoice;
import art.arcane.adapt.platform.recipe.RecipeView;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.config.TomlCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class CraftingScalingTest {
  @TempDir
  Path dataDirectory;

  @BeforeEach
  void installPlatform() {
    TestCores.install(TestPlatform.builder(dataDirectory).build().platform());
  }

  @AfterEach
  void clearPlatform() {
    TestCores.clear();
  }

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
  void thriftyHandsRequiresThreeDistinctActualMaterials() {
    ItemRef logs = ingredient(Id.minecraft("oak_log"), 64);
    ItemRef planks = ingredient(Id.minecraft("oak_planks"), 1);
    ItemRef sticks = ingredient(Id.minecraft("stick"), 1);

    assertThat(CraftingThriftyHands.refundMaterials(Arrays.asList(logs, logs, logs))).isEmpty();
    assertThat(CraftingThriftyHands.refundMaterials(Arrays.asList(logs, planks, planks))).isEmpty();
    assertThat(CraftingThriftyHands.refundMaterials(Arrays.asList(logs, planks, sticks)))
        .containsExactlyInAnyOrder(Id.minecraft("oak_log"), Id.minecraft("oak_planks"), Id.minecraft("stick"));
  }

  @Test
  void thriftyHandsIgnoresEmptyCellsAndUsesTheChosenWoodVariant() {
    ItemRef birch = ingredient(Id.minecraft("birch_planks"), 1);
    ItemRef iron = ingredient(Id.minecraft("iron_ingot"), 1);
    ItemRef redstone = ingredient(Id.minecraft("redstone"), 1);
    ItemRef empty = ingredient(Id.minecraft("air"), 1);
    ItemRef exhausted = ingredient(Id.minecraft("diamond"), 0);

    assertThat(CraftingThriftyHands.refundMaterials(Arrays.asList(birch, iron, redstone, null, empty, exhausted)))
        .containsExactlyInAnyOrder(Id.minecraft("birch_planks"), Id.minecraft("iron_ingot"), Id.minecraft("redstone"));
  }

  @Test
  void thriftyHandsConsumesOnlyOneRefundForCommittedOutput() {
    UUID playerId = UUID.randomUUID();
    CraftItemEvent click = mock(CraftItemEvent.class);
    Map<UUID, CraftingThriftyHands.PendingRefund> refunds = new HashMap<>();
    refunds.put(playerId, new CraftingThriftyHands.PendingRefund(click, Id.minecraft("iron_ingot"), Id.minecraft("piston")));
    ItemRef output = ingredient(Id.minecraft("piston"), 16);

    assertThat(CraftingThriftyHands.committedRefund(refunds, playerId, output)).isEqualTo(Id.minecraft("iron_ingot"));
    assertThat(CraftingThriftyHands.committedRefund(refunds, playerId, output)).isNull();
  }

  @Test
  void thriftyHandsRejectsCancelledClicksAndMissingOrEmptyCommittedOutput() {
    UUID playerId = UUID.randomUUID();
    CraftItemEvent click = mock(CraftItemEvent.class);
    Map<UUID, CraftingThriftyHands.PendingRefund> refunds = new HashMap<>();
    CraftingThriftyHands.PendingRefund pending = new CraftingThriftyHands.PendingRefund(click, Id.minecraft("iron_ingot"), Id.minecraft("piston"));
    refunds.put(playerId, pending);
    assertThat(CraftingThriftyHands.committedRefund(refunds, playerId, ingredient(Id.minecraft("piston"), 0))).isNull();
    refunds.put(playerId, pending);
    assertThat(CraftingThriftyHands.committedRefund(refunds, playerId, ingredient(Id.minecraft("lever"), 1))).isNull();
    refunds.put(playerId, pending);
    when(click.dispatchCancelled()).thenReturn(true);
    assertThat(CraftingThriftyHands.committedRefund(refunds, playerId, ingredient(Id.minecraft("piston"), 1))).isNull();
    refunds.put(playerId, pending);
    refunds.remove(playerId, pending);
    assertThat(CraftingThriftyHands.committedRefund(refunds, playerId, ingredient(Id.minecraft("piston"), 1))).isNull();
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
    WorldRef world = mock(WorldRef.class);
    Loc furnace = new Loc(world, 0D, 64D, 0D);
    PlayerRef outside = playerAt(world, 9D, 64D, 0D);
    PlayerRef inactive = playerAt(world, 1D, 64D, 0D);
    PlayerRef firstTie = playerAt(world, 2D, 64D, 0D);
    PlayerRef secondTie = playerAt(world, -2D, 64D, 0D);

    PlayerRef selected = CraftingProvisioner.nearestEligiblePlayer(
        furnace,
        8D,
        List.of(outside, inactive, firstTie, secondTie),
        player -> player != inactive
    );

    assertThat(selected).isSameAs(firstTie);
  }

  @Test
  void bundleRecipeWrapsAChestInLeather() {
    assertThat(CraftingBackpacks.recipeShape()).isEqualTo(List.of("LLL", "LCL", "LLL"));
  }

  @Test
  void compactorIsOneLevelAndCoversEveryMaterial() {
    assertThat(new CraftingCompactor.Config().maxLevel).isEqualTo(1);
    assertThat(CraftingCompactor.materialsCovered()).isEqualTo(13);
    int glowstoneUnitsPerBlock = CraftingCompactor.unitsPerBlock(Id.minecraft("glowstone_dust"));
    assertThat(glowstoneUnitsPerBlock).isEqualTo(4);
    assertThat(CraftingCompactor.blocksFor(64, glowstoneUnitsPerBlock)).isEqualTo(16);
    assertThat(CraftingCompactor.unitsConsumed(16, glowstoneUnitsPerBlock)).isEqualTo(64);

    int coalUnitsPerBlock = CraftingCompactor.unitsPerBlock(Id.minecraft("coal"));
    assertThat(coalUnitsPerBlock).isEqualTo(9);
    assertThat(CraftingCompactor.blocksFor(64, coalUnitsPerBlock)).isEqualTo(7);
    assertThat(CraftingCompactor.unitsConsumed(7, coalUnitsPerBlock)).isEqualTo(63);
  }

  @Test
  void deconstructionCountsEveryShapedRecipeSlot() {
    String[] chestShape = {"PPP", "P P", "PPP"};
    Map<Id, Integer> ingredientCounts = CraftingDeconstruction.shapedIngredientCounts(
        chestShape,
        Map.of('P', Id.minecraft("oak_planks"))
    );
    int ingredients = CraftingDeconstruction.shapedIngredientCount(
        chestShape,
        Map.of('P', Id.minecraft("oak_planks"))
    );

    assertThat(ingredientCounts).containsExactly(Map.entry(Id.minecraft("oak_planks"), 8));
    assertThat(ingredients).isEqualTo(8);
    assertThat(CraftingDeconstruction.salvageAmount(ingredients, 1, 1)).isEqualTo(4);
    assertThat(CraftingDeconstruction.salvageAmount(ingredients, 64, 1)).isEqualTo(256);
    assertThat(CraftingDeconstruction.splitAmounts(256, 64)).containsExactly(64, 64, 64, 64);
  }

  @Test
  void deconstructionIgnoresNativeEmptyRecipeCells() {
    String[] shape = {"abc", "def", "ghi"};
    Map<Character, Object> choices = new HashMap<>();
    for (char symbol : "acdefghi".toCharArray()) {
      choices.put(symbol, Id.minecraft("iron_ingot"));
    }
    choices.put('b', null);

    assertThat(CraftingDeconstruction.shapedIngredientCount(shape, choices)).isEqualTo(8);
    choices.remove('b');
    assertThat(CraftingDeconstruction.shapedIngredientCount(shape, choices)).isEqualTo(8);
  }

  @Test
  void deconstructionOffersFourIngotsFromNativeChestplateRecipe() {
    Id ingot = Id.minecraft("iron_ingot");
    RecipeChoice iron = RecipeChoice.ofTypes(List.of(ingot));
    Map<Character, RecipeChoice> choices = new HashMap<>();
    for (char symbol : "acdefghi".toCharArray()) {
      choices.put(symbol, iron);
    }
    ItemRef source = new FakeItem(Id.minecraft("iron_chestplate"), 1);
    RecipeView recipe = new RecipeView(Id.minecraft("iron_chestplate"), RecipeView.Kind.SHAPED,
        List.of("abc", "def", "ghi"), choices, List.of(), source);

    List<ItemRef> offerings = CraftingDeconstruction.getDeconstructionOfferings(source, recipe);
    assertThat(offerings).hasSize(1);
    assertThat(offerings.getFirst().type()).isEqualTo(ingot);
    assertThat(offerings.getFirst().amount()).isEqualTo(4);
  }

  @Test
  void deconstructionNeverSalvagesGolemItems() {
    ItemRef dormantGolem = new FakeItem(Id.minecraft("iron_block"), 1);
    ItemRef ironBlock = new FakeItem(Id.minecraft("iron_block"), 1);

    try (MockedStatic<GolemItems> items = mockStatic(GolemItems.class)) {
      items.when(() -> GolemItems.isGolemItem(dormantGolem)).thenReturn(true);
      items.when(() -> GolemItems.isGolemItem(ironBlock)).thenReturn(false);

      assertThat(CraftingDeconstruction.salvageable(dormantGolem)).isFalse();
      assertThat(CraftingDeconstruction.salvageable(ironBlock)).isTrue();
    }
  }

  @Test
  void deconstructionAcceptsOnlyFullyRepairedArmorAndUsesPlainRecipeLookup() {
    FakeItem repairedArmor = new FakeItem(Id.minecraft("iron_leggings"), 1);
    repairedArmor.setDisplayName("Named");

    FakeItem damagedArmor = FakeItem.tool("diamond_chestplate", 528);
    damagedArmor.setDamage(1);

    ItemRef ordinaryItem = new FakeItem(Id.minecraft("chest"), 1);

    assertThat(CraftingDeconstruction.hasEligibleRepairState(repairedArmor)).isTrue();
    assertThat(CraftingDeconstruction.hasEligibleRepairState(damagedArmor)).isFalse();
    assertThat(CraftingDeconstruction.hasEligibleRepairState(ordinaryItem)).isTrue();
    ItemRef recipeLookup = CraftingDeconstruction.recipeLookupItem(repairedArmor);
    assertThat(recipeLookup).isNotSameAs(repairedArmor);
    assertThat(recipeLookup.type()).isEqualTo(Id.minecraft("iron_leggings"));
    assertThat(recipeLookup.amount()).isEqualTo(1);
    assertThat(recipeLookup.hasDisplayName()).isFalse();
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
    assertThat(CraftingCompactor.isActivation(true, 1, true, Id.minecraft("crafting_table"))).isTrue();
    assertThat(CraftingCompactor.isActivation(false, 1, true, Id.minecraft("crafting_table"))).isFalse();
    assertThat(CraftingCompactor.isActivation(true, 0, true, Id.minecraft("crafting_table"))).isFalse();
    assertThat(CraftingCompactor.isActivation(true, 1, false, Id.minecraft("crafting_table"))).isFalse();
    assertThat(CraftingCompactor.isActivation(true, 1, true, Id.minecraft("stone"))).isFalse();
  }

  @Test
  void compactorOwnsItsExactSwapGestureWhenAlreadyCancelled() throws ReflectiveOperationException {
    Method handler = CraftingCompactor.class.getDeclaredMethod("on", PlayerSwapHandItemsEvent.class);
    Handle handle = handler.getAnnotation(Handle.class);

    assertThat(handler.isAnnotationPresent(ReceiveCancelledEvents.class)).isTrue();
    assertThat(ComponentRegistrar.ignoresCancelled(new HandlerMethod(handler, PlayerSwapHandItemsEvent.class,
        handle.priority(), handle.ignoreCancelled(), (Object owner, AdaptEvent event) -> { }))).isFalse();
  }

  private static PlayerRef playerAt(WorldRef world, double x, double y, double z) {
    PlayerRef player = mock(PlayerRef.class);
    when(player.location()).thenReturn(new Loc(world, x, y, z));
    return player;
  }

  private static ItemRef ingredient(Id material, int amount) {
    return new FakeItem(material, amount);
  }
}
