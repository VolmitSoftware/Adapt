package art.arcane.adapt.api.golem.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Crafter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
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
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.StonecutterInventory;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemItemGuardTest {
  private static final NamespacedKey VANILLA_RECIPE = NamespacedKey.minecraft("iron_ingot_from_iron_block");
  private static final NamespacedKey GOLEMANCY_RECIPE = new NamespacedKey("adapt", "golemancy-guardian-core");

  private final GolemItemGuard guard = new GolemItemGuard();

  @Test
  void golemancyRecipesAreAdaptKeysWithTheGolemancyPrefix() {
    assertThat(GolemItemGuard.isGolemancyRecipe(GOLEMANCY_RECIPE)).isTrue();
    assertThat(GolemItemGuard.isGolemancyRecipe(new NamespacedKey("adapt", "golemancy-artificers-crook"))).isTrue();
    assertThat(GolemItemGuard.isGolemancyRecipe(new NamespacedKey("adapt", "crafting-backpack"))).isFalse();
    assertThat(GolemItemGuard.isGolemancyRecipe(new NamespacedKey("other", "golemancy-crook"))).isFalse();
    assertThat(GolemItemGuard.isGolemancyRecipe(VANILLA_RECIPE)).isFalse();
    assertThat(GolemItemGuard.isGolemancyRecipe(null)).isFalse();
  }

  @Test
  void golemItemIngredientsBlockEveryRecipeExceptGolemancyRecipes() {
    assertThat(GolemItemGuard.blocksRecipe(VANILLA_RECIPE, true)).isTrue();
    assertThat(GolemItemGuard.blocksRecipe(new NamespacedKey("adapt", "crafting-backpack"), true)).isTrue();
    assertThat(GolemItemGuard.blocksRecipe(null, true)).isTrue();
    assertThat(GolemItemGuard.blocksRecipe(GOLEMANCY_RECIPE, true)).isFalse();
    assertThat(GolemItemGuard.blocksRecipe(VANILLA_RECIPE, false)).isFalse();
    assertThat(GolemItemGuard.blocksRecipe(null, false)).isFalse();
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
  void golemItemsNeverPlaceAsBlocks() {
    BlockPlaceEvent dormant = place(GolemItemStacks.tagged(Material.IRON_BLOCK, "dormant_golem", null));
    BlockPlaceEvent plain = place(GolemItemStacks.plain(Material.IRON_BLOCK));

    guard.on(dormant);
    guard.on(plain);

    assertThat(dormant.isCancelled()).isTrue();
    assertThat(plain.isCancelled()).isFalse();
  }

  @Test
  void craftingGridResultsClearForGolemItemsOutsideGolemancyRecipes() {
    CraftingInventory vanilla = craftingGrid(VANILLA_RECIPE, GolemItemStacks.tagged(Material.IRON_BLOCK, "dormant_golem", null));
    CraftingInventory golemancy = craftingGrid(GOLEMANCY_RECIPE, GolemItemStacks.tagged(Material.FIREWORK_STAR, "blank_core", null));
    CraftingInventory plain = craftingGrid(VANILLA_RECIPE, GolemItemStacks.plain(Material.IRON_BLOCK));

    guard.on(new PrepareItemCraftEvent(vanilla, view(vanilla), false));
    guard.on(new PrepareItemCraftEvent(golemancy, view(golemancy), false));
    guard.on(new PrepareItemCraftEvent(plain, view(plain), false));

    verify(vanilla).setResult(null);
    verify(golemancy, never()).setResult(any());
    verify(plain, never()).setResult(any());
  }

  @Test
  void craftersRefuseGolemItemsOutsideGolemancyRecipes() {
    CrafterCraftEvent vanilla = crafterCraft(VANILLA_RECIPE, GolemItemStacks.tagged(Material.IRON_BLOCK, "dormant_golem", null));
    CrafterCraftEvent golemancy = crafterCraft(GOLEMANCY_RECIPE, GolemItemStacks.tagged(Material.FIREWORK_STAR, "blank_core", null));
    CrafterCraftEvent plain = crafterCraft(VANILLA_RECIPE, GolemItemStacks.plain(Material.IRON_BLOCK));

    guard.on(vanilla);
    guard.on(golemancy);
    guard.on(plain);

    assertThat(vanilla.isCancelled()).isTrue();
    assertThat(golemancy.isCancelled()).isFalse();
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
    GrindstoneInventory plainGrindstone = inventory(GrindstoneInventory.class, GolemItemStacks.plain(Material.IRON_HOE), null);
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
  void furnacesNeverCookGolemItems() {
    FurnaceSmeltEvent node = smelt(GolemItemStacks.tagged(Material.CLAY_BALL, "blank_node", null));
    FurnaceSmeltEvent clay = smelt(GolemItemStacks.plain(Material.CLAY_BALL));

    guard.on(node);
    guard.on(clay);

    assertThat(node.isCancelled()).isTrue();
    assertThat(clay.isCancelled()).isFalse();
  }

  @Test
  void stonecutterAndMerchantResultsLockWhileAGolemItemIsAnInput() {
    ItemStack dormantCopper = GolemItemStacks.tagged(Material.COPPER_BLOCK, "dormant_golem", null);
    InventoryClickEvent stonecutter = resultClick(inventory(StonecutterInventory.class, dormantCopper, null));
    InventoryClickEvent merchant = resultClick(inventory(MerchantInventory.class,
        GolemItemStacks.tagged(Material.CLAY_BALL, "blank_node", null), null, null));
    InventoryClickEvent plainStonecutter = resultClick(inventory(StonecutterInventory.class,
        GolemItemStacks.plain(Material.COPPER_BLOCK), null));
    InventoryClickEvent workbench = resultClick(inventory(CraftingInventory.class, null, dormantCopper));

    guard.on(stonecutter);
    guard.on(merchant);
    guard.on(plainStonecutter);
    guard.on(workbench);

    assertThat(stonecutter.isCancelled()).isTrue();
    assertThat(merchant.isCancelled()).isTrue();
    assertThat(plainStonecutter.isCancelled()).isFalse();
    assertThat(workbench.isCancelled()).isFalse();
  }

  @Test
  void dispensersHoldGolemItems() {
    BlockDispenseEvent crook = new BlockDispenseEvent(mock(Block.class), crook(), new Vector());
    BlockDispenseEvent hoe = new BlockDispenseEvent(mock(Block.class), GolemItemStacks.plain(Material.IRON_HOE), new Vector());

    guard.on(crook);
    guard.on(hoe);

    assertThat(crook.isCancelled()).isTrue();
    assertThat(hoe.isCancelled()).isFalse();
  }

  private static ItemStack crook() {
    return GolemItemStacks.tagged(Material.IRON_HOE, "crook", null);
  }

  private static PlayerInteractEvent interact(Action action, ItemStack item, EquipmentSlot hand) {
    Block block = action == Action.RIGHT_CLICK_AIR ? null : mock(Block.class);
    return new PlayerInteractEvent(mock(Player.class), action, item, block, BlockFace.UP, hand);
  }

  private static BlockPlaceEvent place(ItemStack item) {
    return new BlockPlaceEvent(mock(Block.class), mock(BlockState.class), mock(Block.class), item, mock(Player.class), true, EquipmentSlot.HAND);
  }

  private static CraftingInventory craftingGrid(NamespacedKey recipeKey, ItemStack ingredient) {
    ShapedRecipe recipe = mock(ShapedRecipe.class);
    when(recipe.getKey()).thenReturn(recipeKey);
    CraftingInventory inventory = mock(CraftingInventory.class);
    when(inventory.getRecipe()).thenReturn(recipe);
    when(inventory.getMatrix()).thenReturn(new ItemStack[]{null, ingredient, null});
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

  private static FurnaceSmeltEvent smelt(ItemStack source) {
    return new FurnaceSmeltEvent(mock(Block.class), source, mock(ItemStack.class), mock(CookingRecipe.class));
  }

  private static InventoryClickEvent resultClick(Inventory top) {
    return new InventoryClickEvent(view(top), InventoryType.SlotType.RESULT, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
  }

  private static <T extends Inventory> T inventory(Class<T> type, ItemStack... contents) {
    T inventory = mock(type);
    when(inventory.getContents()).thenReturn(contents);
    return inventory;
  }

  private static InventoryView view(Inventory top) {
    InventoryView view = mock(InventoryView.class);
    when(view.getTopInventory()).thenReturn(top);
    return view;
  }
}
