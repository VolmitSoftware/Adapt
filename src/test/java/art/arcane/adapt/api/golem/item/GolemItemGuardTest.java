package art.arcane.adapt.api.golem.item;

import art.arcane.adapt.api.recipe.ExactIngredient;
import art.arcane.adapt.api.recipe.ExactIngredientBook;
import art.arcane.adapt.util.common.misc.CustomModel;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Crafter;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.StonecutterInventory;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.inventory.view.EnchantmentView;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemItemGuardTest {
  private static final AtomicLong GENERATIONS = new AtomicLong(1_000_000L);
  private static final NamespacedKey VANILLA_RECIPE = NamespacedKey.minecraft("iron_ingot_from_iron_block");
  private static final NamespacedKey GOLEMANCY_RECIPE = new NamespacedKey("adapt", "golemancy-guardian-core");
  private static final NamespacedKey GOLEMANCY_CROOK_RECIPE = new NamespacedKey("adapt", "golemancy-artificers-crook");
  private static final ExactIngredient BLANK_CORE = spec(GolemItemKind.BLANK_CORE, Material.FIREWORK_STAR);
  private static final ExactIngredient BLANK_NODE = spec(GolemItemKind.BLANK_NODE, Material.CLAY_BALL);

  private final ExactIngredientBook book = new ExactIngredientBook();
  private final GolemItemGuard guard = new GolemItemGuard(book);

  @Test
  void golemancyRecipesAreAdaptKeysWithTheGolemancyPrefix() {
    assertThat(GolemItemGuard.isGolemancyRecipe(GOLEMANCY_RECIPE)).isTrue();
    assertThat(GolemItemGuard.isGolemancyRecipe(GOLEMANCY_CROOK_RECIPE)).isTrue();
    assertThat(GolemItemGuard.isGolemancyRecipe(new NamespacedKey("adapt", "crafting-backpack"))).isFalse();
    assertThat(GolemItemGuard.isGolemancyRecipe(new NamespacedKey("other", "golemancy-crook"))).isFalse();
    assertThat(GolemItemGuard.isGolemancyRecipe(VANILLA_RECIPE)).isFalse();
    assertThat(GolemItemGuard.isGolemancyRecipe(null)).isFalse();
  }

  @Test
  void golemItemsBlockEveryRecipeOutsideGolemancy() {
    List<ItemStack> dormant = List.of(GolemItemStacks.tagged(Material.IRON_BLOCK, "dormant_golem", null));

    assertThat(guard.blocksRecipe(VANILLA_RECIPE, dormant)).isTrue();
    assertThat(guard.blocksRecipe(new NamespacedKey("adapt", "crafting-backpack"), dormant)).isTrue();
    assertThat(guard.blocksRecipe(null, dormant)).isTrue();
    assertThat(guard.blocksRecipe(VANILLA_RECIPE, List.of())).isFalse();
    assertThat(guard.blocksRecipe(null, List.of())).isFalse();
  }

  @Test
  void golemancyRecipesNeedEveryRequiredGolemItemAndNoOther() {
    book.set(GOLEMANCY_RECIPE, List.of(BLANK_CORE, BLANK_NODE));
    ItemStack core = blankCore();
    ItemStack node = GolemItemStacks.tagged(Material.CLAY_BALL, "blank_node", null);

    assertThat(guard.blocksRecipe(GOLEMANCY_RECIPE, List.of(node, core))).isFalse();
    assertThat(guard.blocksRecipe(GOLEMANCY_RECIPE, List.of(core))).isTrue();
    assertThat(guard.blocksRecipe(GOLEMANCY_RECIPE, List.of(core, node, crook()))).isTrue();
    assertThat(guard.blocksRecipe(GOLEMANCY_RECIPE, List.of(core, crook()))).isTrue();
    assertThat(guard.blocksRecipe(GOLEMANCY_RECIPE, List.of(core, blankCore()))).isTrue();
  }

  @Test
  void plainBaseItemsNeverStandInForGolemIngredients() {
    book.set(GOLEMANCY_RECIPE, List.of(BLANK_CORE));
    CraftingInventory plainStar = craftingGrid(GOLEMANCY_RECIPE, GolemItemStacks.plain(Material.FIREWORK_STAR), GolemItemStacks.plain(Material.IRON_INGOT));
    CraftingInventory taggedCore = craftingGrid(GOLEMANCY_RECIPE, blankCore(), GolemItemStacks.plain(Material.IRON_INGOT));

    guard.on(new PrepareItemCraftEvent(plainStar, view(plainStar), false));
    guard.on(new PrepareItemCraftEvent(taggedCore, view(taggedCore), false));

    verify(plainStar).setResult(null);
    verify(taggedCore, never()).setResult(any());
  }

  @Test
  void golemancyRecipesWithoutGolemIngredientsRefuseGolemItemsInMaterialSlots() {
    book.set(GOLEMANCY_CROOK_RECIPE, List.of());
    CraftingInventory plainHoe = craftingGrid(GOLEMANCY_CROOK_RECIPE, GolemItemStacks.plain(Material.IRON_HOE));
    CraftingInventory crookAsHoe = craftingGrid(GOLEMANCY_CROOK_RECIPE, crook());

    guard.on(new PrepareItemCraftEvent(plainHoe, view(plainHoe), false));
    guard.on(new PrepareItemCraftEvent(crookAsHoe, view(crookAsHoe), false));

    verify(plainHoe, never()).setResult(any());
    verify(crookAsHoe).setResult(null);
  }

  @Test
  void golemancyRecipesWithoutAnIngredientBookEntryNeverCraft() {
    CraftingInventory plainGrid = craftingGrid(GOLEMANCY_CROOK_RECIPE, GolemItemStacks.plain(Material.IRON_HOE));

    guard.on(new PrepareItemCraftEvent(plainGrid, view(plainGrid), false));

    assertThat(guard.blocksRecipe(GOLEMANCY_CROOK_RECIPE, List.of())).isTrue();
    verify(plainGrid).setResult(null);
  }

  @Test
  void craftingGridResultsClearForGolemItemsOutsideGolemancyRecipes() {
    book.set(GOLEMANCY_RECIPE, List.of(BLANK_CORE));
    CraftingInventory vanilla = craftingGrid(VANILLA_RECIPE, GolemItemStacks.tagged(Material.IRON_BLOCK, "dormant_golem", null));
    CraftingInventory golemancy = craftingGrid(GOLEMANCY_RECIPE, blankCore());
    CraftingInventory plain = craftingGrid(VANILLA_RECIPE, GolemItemStacks.plain(Material.IRON_BLOCK));

    guard.on(new PrepareItemCraftEvent(vanilla, view(vanilla), false));
    guard.on(new PrepareItemCraftEvent(golemancy, view(golemancy), false));
    guard.on(new PrepareItemCraftEvent(plain, view(plain), false));

    verify(vanilla).setResult(null);
    verify(golemancy, never()).setResult(any());
    verify(plain, never()).setResult(any());
  }

  @Test
  void craftersFollowTheSameRecipeRules() {
    book.set(GOLEMANCY_RECIPE, List.of(BLANK_CORE));
    CrafterCraftEvent vanilla = crafterCraft(VANILLA_RECIPE, GolemItemStacks.tagged(Material.IRON_BLOCK, "dormant_golem", null));
    CrafterCraftEvent golemancy = crafterCraft(GOLEMANCY_RECIPE, blankCore());
    CrafterCraftEvent plainStar = crafterCraft(GOLEMANCY_RECIPE, GolemItemStacks.plain(Material.FIREWORK_STAR));
    CrafterCraftEvent plain = crafterCraft(VANILLA_RECIPE, GolemItemStacks.plain(Material.IRON_BLOCK));

    guard.on(vanilla);
    guard.on(golemancy);
    guard.on(plainStar);
    guard.on(plain);

    assertThat(vanilla.isCancelled()).isTrue();
    assertThat(golemancy.isCancelled()).isFalse();
    assertThat(plainStar.isCancelled()).isTrue();
    assertThat(plain.isCancelled()).isFalse();
  }

  @Test
  void crookUseIsDeniedInEitherHandWhileTheClickedBlockStillWorks() {
    for (EquipmentSlot hand : new EquipmentSlot[]{EquipmentSlot.HAND, EquipmentSlot.OFF_HAND}) {
      PlayerInteractEvent click = interact(Action.RIGHT_CLICK_BLOCK, crook(), hand);

      guard.on(click);

      assertThat(click.useItemInHand()).describedAs("%s item use", hand).isEqualTo(Event.Result.DENY);
      assertThat(click.useInteractedBlock()).describedAs("%s block use", hand).isEqualTo(Event.Result.ALLOW);
    }
    PlayerInteractEvent air = interact(Action.RIGHT_CLICK_AIR, crook(), EquipmentSlot.HAND);
    guard.on(air);
    assertThat(air.useItemInHand()).isEqualTo(Event.Result.DENY);
  }

  @Test
  void plainItemsAndLeftClicksKeepTheirVanillaUse() {
    PlayerInteractEvent plainHoe = interact(Action.RIGHT_CLICK_BLOCK, GolemItemStacks.plain(Material.IRON_HOE), EquipmentSlot.HAND);
    PlayerInteractEvent leftCrook = interact(Action.LEFT_CLICK_BLOCK, crook(), EquipmentSlot.HAND);

    guard.on(plainHoe);
    guard.on(leftCrook);

    assertThat(plainHoe.useItemInHand()).isEqualTo(Event.Result.DEFAULT);
    assertThat(leftCrook.useItemInHand()).isEqualTo(Event.Result.DEFAULT);
  }

  @Test
  void dormantGolemClicksReachBlockPlacementSoTheDeployCanHookIt() {
    PlayerInteractEvent click = interact(Action.RIGHT_CLICK_BLOCK, GolemItemStacks.tagged(Material.IRON_BLOCK, "dormant_golem", null), EquipmentSlot.HAND);

    guard.on(click);

    assertThat(click.useItemInHand()).isEqualTo(Event.Result.DEFAULT);
  }

  @Test
  void golemItemsNeverPlaceAsBlocksButDormantGolemsReachTheirPlacementListener() {
    BlockPlaceEvent dormant = place(GolemItemStacks.tagged(Material.IRON_BLOCK, "dormant_golem", null));
    BlockPlaceEvent node = place(GolemItemStacks.tagged(Material.BRICK, "node", null));
    BlockPlaceEvent plain = place(GolemItemStacks.plain(Material.IRON_BLOCK));

    guard.on(dormant);
    guard.on(node);
    guard.on(plain);

    assertThat(dormant.isCancelled()).isFalse();
    assertThat(node.isCancelled()).isTrue();
    assertThat(plain.isCancelled()).isFalse();
  }

  @Test
  void theMonitorSafetyNetCancelsADormantGolemPlacementNothingElseCancelled() {
    BlockPlaceEvent dormant = place(GolemItemStacks.tagged(Material.WAXED_EXPOSED_COPPER, "dormant_golem", null));
    BlockPlaceEvent plain = place(GolemItemStacks.plain(Material.SNOW_BLOCK));

    guard.placed(dormant);
    guard.placed(plain);

    assertThat(dormant.isCancelled()).isTrue();
    assertThat(plain.isCancelled()).isFalse();
  }

  @Test
  void smithingAnvilAndGrindstoneResultsClearForGolemItems() {
    ItemStack crook = crook();
    SmithingInventory smithingTable = inventory(SmithingInventory.class, null, crook, null);
    AnvilInventory anvil = inventory(AnvilInventory.class, crook, null);
    GrindstoneInventory grindstone = inventory(GrindstoneInventory.class, crook, null);
    AnvilView anvilView = mock(AnvilView.class);
    when(anvilView.getTopInventory()).thenReturn(anvil);
    PrepareSmithingEvent smithing = new PrepareSmithingEvent(view(smithingTable), mock(ItemStack.class));
    PrepareAnvilEvent anvilEvent = new PrepareAnvilEvent(anvilView, mock(ItemStack.class));
    PrepareGrindstoneEvent grindstoneEvent = new PrepareGrindstoneEvent(view(grindstone), mock(ItemStack.class));
    GrindstoneInventory plainGrindstone = inventory(GrindstoneInventory.class, GolemItemStacks.plain(Material.IRON_HOE), null, crook());
    ItemStack plainResult = mock(ItemStack.class);
    PrepareGrindstoneEvent plainEvent = new PrepareGrindstoneEvent(view(plainGrindstone), plainResult);

    guard.on(smithing);
    guard.on(anvilEvent);
    guard.on(grindstoneEvent);
    guard.on(plainEvent);

    assertThat(smithing.getResult()).isNull();
    assertThat(anvilEvent.getResult()).isNull();
    assertThat(grindstoneEvent.getResult()).isNull();
    assertThat(plainEvent.getResult()).isSameAs(plainResult);
  }

  @Test
  void enchantingTablesRefuseGolemItems() {
    PrepareItemEnchantEvent prepareCrook = prepareEnchant(crook());
    PrepareItemEnchantEvent prepareHoe = prepareEnchant(GolemItemStacks.plain(Material.IRON_HOE));
    EnchantItemEvent enchantCrook = enchant(crook());
    EnchantItemEvent enchantHoe = enchant(GolemItemStacks.plain(Material.IRON_HOE));

    guard.on(prepareCrook);
    guard.on(prepareHoe);
    guard.on(enchantCrook);
    guard.on(enchantHoe);

    assertThat(prepareCrook.isCancelled()).isTrue();
    assertThat(prepareHoe.isCancelled()).isFalse();
    assertThat(enchantCrook.isCancelled()).isTrue();
    assertThat(enchantHoe.isCancelled()).isFalse();
  }

  @Test
  void furnacesNeverCookGolemItems() {
    FurnaceSmeltEvent node = smelt(GolemItemStacks.tagged(Material.CLAY_BALL, "blank_node", null));
    FurnaceSmeltEvent clay = smelt(GolemItemStacks.plain(Material.CLAY_BALL));

    guard.on(node);
    guard.on(clay);

    assertThat(node.isCancelled()).isTrue();
    assertThat(clay.isCancelled()).isFalse();
  }

  @Test
  void stonecuttersCheckOnlyTheirInput() {
    ItemStack dormantCopper = GolemItemStacks.tagged(Material.COPPER_BLOCK, "dormant_golem", null);
    InventoryClickEvent taggedInput = resultClick(stonecutter(dormantCopper, GolemItemStacks.plain(Material.CUT_COPPER)));
    InventoryClickEvent taggedResultOnly = resultClick(stonecutter(GolemItemStacks.plain(Material.COPPER_BLOCK), dormantCopper));
    InventoryClickEvent workbench = resultClick(inventory(CraftingInventory.class, null, dormantCopper));

    guard.on(taggedInput);
    guard.on(taggedResultOnly);
    guard.on(workbench);

    assertThat(taggedInput.isCancelled()).isTrue();
    assertThat(taggedResultOnly.isCancelled()).isFalse();
    assertThat(workbench.isCancelled()).isFalse();
  }

  @Test
  void merchantsRefuseOnlyTradesThatWouldSwallowAGolemItemAsAPlainItem() {
    ItemStack node = GolemItemStacks.tagged(Material.CLAY_BALL, "blank_node", null);
    ItemStack plainClay = GolemItemStacks.plain(Material.CLAY_BALL);
    ItemStack emerald = GolemItemStacks.plain(Material.EMERALD);
    InventoryClickEvent genericTrade = resultClick(merchant(node, null, plainClay, emerald));
    InventoryClickEvent golemTrade = resultClick(merchant(node, null, GolemItemStacks.tagged(Material.CLAY_BALL, "blank_node", null), emerald));
    InventoryClickEvent buyingAGolemItem = resultClick(merchant(emerald, null, emerald, node));
    InventoryClickEvent plainTrade = resultClick(merchant(plainClay, null, plainClay, emerald));

    guard.on(genericTrade);
    guard.on(golemTrade);
    guard.on(buyingAGolemItem);
    guard.on(plainTrade);

    assertThat(genericTrade.isCancelled()).isTrue();
    assertThat(golemTrade.isCancelled()).isFalse();
    assertThat(buyingAGolemItem.isCancelled()).isFalse();
    assertThat(plainTrade.isCancelled()).isFalse();
  }

  @Test
  void dispensersHoldGolemItemsWhileDroppersStillMoveThem() {
    BlockDispenseEvent dispenser = new BlockDispenseEvent(block(Material.DISPENSER), crook(), new Vector());
    BlockDispenseEvent dropper = new BlockDispenseEvent(block(Material.DROPPER), crook(), new Vector());
    BlockDispenseEvent hoe = new BlockDispenseEvent(block(Material.DISPENSER), GolemItemStacks.plain(Material.IRON_HOE), new Vector());

    guard.on(dispenser);
    guard.on(dropper);
    guard.on(hoe);

    assertThat(dispenser.isCancelled()).isTrue();
    assertThat(dropper.isCancelled()).isFalse();
    assertThat(hoe.isCancelled()).isFalse();
  }

  @Test
  void hoppersNeverPickUpShatteredCoresOrDormantGolems() {
    InventoryPickupItemEvent shattered = hopperPickup(GolemItemStacks.tagged(Material.FIREWORK_STAR, "shattered_core", null));
    InventoryPickupItemEvent dormant = hopperPickup(GolemItemStacks.tagged(Material.IRON_BLOCK, "dormant_golem", null));
    InventoryPickupItemEvent core = hopperPickup(GolemItemStacks.tagged(Material.FIREWORK_STAR, "blank_core", null));
    InventoryPickupItemEvent plain = hopperPickup(GolemItemStacks.plain(Material.FIREWORK_STAR));

    for (InventoryPickupItemEvent pickup : List.of(shattered, dormant, core, plain)) {
      guard.on(pickup);
    }

    assertThat(shattered.isCancelled()).isTrue();
    assertThat(dormant.isCancelled()).isTrue();
    assertThat(core.isCancelled()).isFalse();
    assertThat(plain.isCancelled()).isFalse();
  }

  @Test
  void hopperPickupsOfOtherMaterialsNeverReadItemMeta() {
    try (MockedStatic<CustomModel> models = models(Map.of())) {
      ItemStack dirt = GolemItemStacks.plain(Material.DIRT);
      InventoryPickupItemEvent pickup = hopperPickup(dirt);

      guard.on(pickup);

      assertThat(pickup.isCancelled()).isFalse();
      verify(dirt, never()).hasItemMeta();
      verify(dirt, never()).getItemMeta();
    }
  }

  @Test
  void hoppersStillCatchOwnerBoundItemsWhoseModelUsesAnotherMaterial() {
    try (MockedStatic<CustomModel> models = models(Map.of("shattered_core", Material.ECHO_SHARD, "dormant_golem.iron_block", Material.LODESTONE))) {
      InventoryPickupItemEvent core = hopperPickup(GolemItemStacks.tagged(Material.ECHO_SHARD, "shattered_core", null));
      InventoryPickupItemEvent dormant = hopperPickup(GolemItemStacks.tagged(Material.LODESTONE, "dormant_golem", null));
      InventoryPickupItemEvent shard = hopperPickup(GolemItemStacks.plain(Material.ECHO_SHARD));

      guard.on(core);
      guard.on(dormant);
      guard.on(shard);

      assertThat(core.isCancelled()).isTrue();
      assertThat(dormant.isCancelled()).isTrue();
      assertThat(shard.isCancelled()).isFalse();
    }
  }

  @Test
  void aModelReloadRefreshesTheOwnerBoundMaterials() {
    try (MockedStatic<CustomModel> models = models(Map.of("shattered_core", Material.ECHO_SHARD))) {
      assertThat(GolemItems.mayBeOwnerBound(Material.ECHO_SHARD)).isTrue();
      long reloaded = GENERATIONS.incrementAndGet();
      models.when(CustomModel::generation).thenReturn(reloaded);
      models.when(() -> CustomModel.get(any(Material.class), any(String[].class)))
          .thenAnswer(call -> new CustomModel(call.getArgument(0), 0, CustomModel.EMPTY_KEY, null));

      assertThat(GolemItems.mayBeOwnerBound(Material.ECHO_SHARD)).isFalse();
      assertThat(GolemItems.mayBeOwnerBound(Material.FIREWORK_STAR)).isTrue();
    }
  }

  private static MockedStatic<CustomModel> models(Map<String, Material> overrides) {
    MockedStatic<CustomModel> models = mockStatic(CustomModel.class);
    models.when(CustomModel::generation).thenReturn(GENERATIONS.incrementAndGet());
    models.when(() -> CustomModel.get(any(Material.class), any(String[].class))).thenAnswer(call -> {
      String[] path = (String[]) call.getRawArguments()[1];
      String key = String.join(".", Arrays.copyOfRange(path, 1, path.length));
      return new CustomModel(overrides.getOrDefault(key, call.getArgument(0)), 0, CustomModel.EMPTY_KEY, null);
    });
    return models;
  }

  private static InventoryPickupItemEvent hopperPickup(ItemStack stack) {
    Item item = mock(Item.class);
    when(item.getItemStack()).thenReturn(stack);
    return new InventoryPickupItemEvent(mock(Inventory.class), item);
  }

  private static ExactIngredient spec(GolemItemKind kind, Material base) {
    return new ExactIngredient(base, item -> GolemItems.kindOf(item).filter(found -> found == kind).isPresent());
  }

  private static ItemStack crook() {
    return GolemItemStacks.tagged(Material.IRON_HOE, "crook", null);
  }

  private static ItemStack blankCore() {
    return GolemItemStacks.tagged(Material.FIREWORK_STAR, "blank_core", null);
  }

  private static Block block(Material type) {
    Block block = mock(Block.class);
    when(block.getType()).thenReturn(type);
    return block;
  }

  private static PlayerInteractEvent interact(Action action, ItemStack item, EquipmentSlot hand) {
    Block block = action == Action.RIGHT_CLICK_AIR ? null : mock(Block.class);
    return new PlayerInteractEvent(mock(Player.class), action, item, block, BlockFace.UP, hand);
  }

  private static BlockPlaceEvent place(ItemStack item) {
    return new BlockPlaceEvent(mock(Block.class), mock(BlockState.class), mock(Block.class), item, mock(Player.class), true, EquipmentSlot.HAND);
  }

  private static CraftingInventory craftingGrid(NamespacedKey recipeKey, ItemStack... ingredients) {
    ShapedRecipe recipe = mock(ShapedRecipe.class);
    when(recipe.getKey()).thenReturn(recipeKey);
    CraftingInventory inventory = mock(CraftingInventory.class);
    when(inventory.getRecipe()).thenReturn(recipe);
    when(inventory.getMatrix()).thenReturn(ingredients);
    return inventory;
  }

  private static CrafterCraftEvent crafterCraft(NamespacedKey recipeKey, ItemStack ingredient) {
    CraftingRecipe recipe = mock(CraftingRecipe.class);
    when(recipe.getKey()).thenReturn(recipeKey);
    Inventory contents = mock(Inventory.class);
    when(contents.getContents()).thenReturn(new ItemStack[]{ingredient, null});
    Crafter crafter = mock(Crafter.class);
    when(crafter.getInventory()).thenReturn(contents);
    Block block = mock(Block.class);
    when(block.getState()).thenReturn(crafter);
    return new CrafterCraftEvent(block, recipe, mock(ItemStack.class));
  }

  private static PrepareItemEnchantEvent prepareEnchant(ItemStack item) {
    return new PrepareItemEnchantEvent(mock(Player.class), mock(EnchantmentView.class), mock(Block.class), item, new EnchantmentOffer[3], 0);
  }

  private static EnchantItemEvent enchant(ItemStack item) {
    return new EnchantItemEvent(mock(Player.class), mock(InventoryView.class), mock(Block.class), item, 1, new HashMap<>(), null, 0, 0);
  }

  private static FurnaceSmeltEvent smelt(ItemStack source) {
    return new FurnaceSmeltEvent(mock(Block.class), source, mock(ItemStack.class), mock(CookingRecipe.class));
  }

  private static StonecutterInventory stonecutter(ItemStack input, ItemStack result) {
    StonecutterInventory inventory = inventory(StonecutterInventory.class, input, result);
    when(inventory.getInputItem()).thenReturn(input);
    when(inventory.getResult()).thenReturn(result);
    return inventory;
  }

  private static MerchantInventory merchant(ItemStack firstInput, ItemStack secondInput, ItemStack tradeIngredient, ItemStack tradeResult) {
    MerchantRecipe recipe = mock(MerchantRecipe.class);
    when(recipe.getIngredients()).thenReturn(List.of(tradeIngredient));
    when(recipe.getResult()).thenReturn(tradeResult);
    MerchantInventory inventory = inventory(MerchantInventory.class, firstInput, secondInput, tradeResult);
    when(inventory.getSelectedRecipe()).thenReturn(recipe);
    return inventory;
  }

  private static InventoryClickEvent resultClick(Inventory top) {
    return new InventoryClickEvent(view(top), InventoryType.SlotType.RESULT, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
  }

  private static <T extends Inventory> T inventory(Class<T> type, ItemStack... contents) {
    T inventory = mock(type);
    when(inventory.getContents()).thenReturn(contents);
    for (int slot = 0; slot < contents.length; slot++) {
      when(inventory.getItem(slot)).thenReturn(contents[slot]);
    }
    return inventory;
  }

  private static InventoryView view(Inventory top) {
    InventoryView view = mock(InventoryView.class);
    when(view.getTopInventory()).thenReturn(top);
    return view;
  }
}
