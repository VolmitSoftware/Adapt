package art.arcane.adapt.content.golem.item;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.item.ExactIngredient;
import art.arcane.adapt.api.item.ItemArt;
import art.arcane.adapt.engine.item.AdaptItemGuard;
import art.arcane.adapt.engine.item.AdaptItems;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.BlockCookEvent;
import art.arcane.adapt.platform.event.block.BlockDispenseEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.block.CrafterCraftEvent;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.EnchantItemEvent;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.InventoryPickupItemEvent;
import art.arcane.adapt.platform.event.inventory.PrepareAnvilEvent;
import art.arcane.adapt.platform.event.inventory.PrepareItemCraftEvent;
import art.arcane.adapt.platform.event.inventory.PrepareItemEnchantEvent;
import art.arcane.adapt.platform.event.inventory.PrepareResultEvent;
import art.arcane.adapt.platform.event.inventory.SlotKind;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeItems;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemItemGuardTest extends AdaptTestBase {
  private static final AtomicLong GENERATIONS = new AtomicLong(1_000_000L);
  private static final Id VANILLA_RECIPE = Id.minecraft("iron_ingot_from_iron_block");
  private static final Id DECORATED_POT_RECIPE = Id.minecraft("decorated_pot");
  private static final Id GOLEMANCY_RECIPE = Id.of("adapt", "golemancy-guardian-core");
  private static final Id GOLEMANCY_CROOK_RECIPE = Id.of("adapt", "golemancy-artificers-crook");
  private static final ExactIngredient BLANK_CORE = spec(GolemItemKind.BLANK_CORE, Id.minecraft("firework_star"));
  private static final ExactIngredient BLANK_NODE = spec(GolemItemKind.BLANK_NODE, Id.minecraft("clay_ball"));

  private final ExactIngredientBook book = new ExactIngredientBook();
  private final GolemItemGuard guard = new GolemItemGuard(book);
  private final AdaptItemGuard items = new AdaptItemGuard();
  private MockedStatic<AdaptItems> catalog;

  @BeforeEach
  void installItems() {
    catalog = mockStatic(AdaptItems.class, CALLS_REAL_METHODS);
    catalog.when(AdaptItems::items).thenReturn(List.<CustomItem.Definition<?>>of(GolemItems.ITEM));
    when(core.getPlatform().host().items()).thenReturn(new FakeItems());
  }

  @AfterEach
  void removeItems() {
    catalog.close();
  }

  @Test
  void golemancyRecipesAreAdaptKeysWithTheGolemancyPrefix() {
    assertThat(GolemItemGuard.isGolemancyRecipe(GOLEMANCY_RECIPE)).isTrue();
    assertThat(GolemItemGuard.isGolemancyRecipe(GOLEMANCY_CROOK_RECIPE)).isTrue();
    assertThat(GolemItemGuard.isGolemancyRecipe(Id.of("adapt", "crafting-backpack"))).isFalse();
    assertThat(GolemItemGuard.isGolemancyRecipe(Id.of("other", "golemancy-crook"))).isFalse();
    assertThat(GolemItemGuard.isGolemancyRecipe(VANILLA_RECIPE)).isFalse();
    assertThat(GolemItemGuard.isGolemancyRecipe(null)).isFalse();
  }

  @Test
  void golemItemsBlockEveryRecipeOutsideGolemancy() {
    List<ItemRef> dormant = List.of(GolemItemStacks.tagged(Id.minecraft("iron_block"), "dormant_golem", null));

    assertThat(guard.blocksRecipe(VANILLA_RECIPE, dormant)).isTrue();
    assertThat(guard.blocksRecipe(Id.of("adapt", "crafting-backpack"), dormant)).isTrue();
    assertThat(guard.blocksRecipe(null, dormant)).isTrue();
    assertThat(guard.blocksRecipe(VANILLA_RECIPE, List.of())).isFalse();
    assertThat(guard.blocksRecipe(null, List.of())).isFalse();
  }

  @Test
  void golemancyRecipesNeedEveryRequiredGolemItemAndNoOther() {
    book.set(GOLEMANCY_RECIPE, List.of(BLANK_CORE, BLANK_NODE));
    ItemRef core = blankCore();
    ItemRef node = GolemItemStacks.tagged(Id.minecraft("clay_ball"), "blank_node", null);

    assertThat(guard.blocksRecipe(GOLEMANCY_RECIPE, List.of(node, core))).isFalse();
    assertThat(guard.blocksRecipe(GOLEMANCY_RECIPE, List.of(core))).isTrue();
    assertThat(guard.blocksRecipe(GOLEMANCY_RECIPE, List.of(core, node, crook()))).isTrue();
    assertThat(guard.blocksRecipe(GOLEMANCY_RECIPE, List.of(core, crook()))).isTrue();
    assertThat(guard.blocksRecipe(GOLEMANCY_RECIPE, List.of(core, blankCore()))).isTrue();
  }

  @Test
  void plainBaseItemsNeverStandInForGolemIngredients() {
    book.set(GOLEMANCY_RECIPE, List.of(BLANK_CORE));
    PrepareItemCraftEvent plainStar = craft(GOLEMANCY_RECIPE, GolemItemStacks.plain(Id.minecraft("firework_star")), GolemItemStacks.plain(Id.minecraft("iron_ingot")));
    PrepareItemCraftEvent taggedCore = craft(GOLEMANCY_RECIPE, blankCore(), GolemItemStacks.plain(Id.minecraft("iron_ingot")));
    ItemRef taggedResult = taggedCore.result();

    guard.on(plainStar);
    guard.on(taggedCore);

    assertThat(plainStar.result().isEmpty()).isTrue();
    assertThat(taggedCore.result()).isSameAs(taggedResult);
  }

  @Test
  void golemancyRecipesWithoutGolemIngredientsRefuseGolemItemsInMaterialSlots() {
    book.set(GOLEMANCY_CROOK_RECIPE, List.of());
    PrepareItemCraftEvent plainHoe = craft(GOLEMANCY_CROOK_RECIPE, GolemItemStacks.plain(Id.minecraft("iron_hoe")));
    PrepareItemCraftEvent crookAsHoe = craft(GOLEMANCY_CROOK_RECIPE, crook());
    ItemRef plainResult = plainHoe.result();

    guard.on(plainHoe);
    guard.on(crookAsHoe);

    assertThat(plainHoe.result()).isSameAs(plainResult);
    assertThat(crookAsHoe.result().isEmpty()).isTrue();
  }

  @Test
  void golemancyRecipesWithoutAnIngredientBookEntryNeverCraft() {
    PrepareItemCraftEvent plainGrid = craft(GOLEMANCY_CROOK_RECIPE, GolemItemStacks.plain(Id.minecraft("iron_hoe")));

    guard.on(plainGrid);

    assertThat(guard.blocksRecipe(GOLEMANCY_CROOK_RECIPE, List.of())).isTrue();
    assertThat(plainGrid.result().isEmpty()).isTrue();
  }

  @Test
  void craftingGridResultsClearForGolemItemsOutsideGolemancyRecipes() {
    book.set(GOLEMANCY_RECIPE, List.of(BLANK_CORE));
    PrepareItemCraftEvent vanilla = craft(VANILLA_RECIPE, GolemItemStacks.tagged(Id.minecraft("iron_block"), "dormant_golem", null));
    PrepareItemCraftEvent golemancy = craft(GOLEMANCY_RECIPE, blankCore());
    PrepareItemCraftEvent plain = craft(VANILLA_RECIPE, GolemItemStacks.plain(Id.minecraft("iron_block")));
    ItemRef golemancyResult = golemancy.result();
    ItemRef plainResult = plain.result();

    guard.on(vanilla);
    guard.on(golemancy);
    guard.on(plain);

    assertThat(vanilla.result().isEmpty()).isTrue();
    assertThat(golemancy.result()).isSameAs(golemancyResult);
    assertThat(plain.result()).isSameAs(plainResult);
  }

  @Test
  void craftersFollowTheSameRecipeRules() {
    book.set(GOLEMANCY_RECIPE, List.of(BLANK_CORE));
    CrafterCraftEvent vanilla = crafterCraft(VANILLA_RECIPE, GolemItemStacks.tagged(Id.minecraft("iron_block"), "dormant_golem", null));
    CrafterCraftEvent golemancy = crafterCraft(GOLEMANCY_RECIPE, blankCore());
    CrafterCraftEvent plainStar = crafterCraft(GOLEMANCY_RECIPE, GolemItemStacks.plain(Id.minecraft("firework_star")));
    CrafterCraftEvent plain = crafterCraft(VANILLA_RECIPE, GolemItemStacks.plain(Id.minecraft("iron_block")));

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
  void sherdAndBrickGolemItemsNeverCraftADecoratedPot() {
    book.set(GOLEMANCY_RECIPE, List.of(BLANK_CORE));
    for (ItemRef golemItem : potIngredients()) {
      PrepareItemCraftEvent table = craft(DECORATED_POT_RECIPE, GolemItemStacks.plain(Id.minecraft("brick")), golemItem,
          GolemItemStacks.plain(Id.minecraft("brick")), GolemItemStacks.plain(Id.minecraft("brick")));
      CrafterCraftEvent crafter = crafterCraft(DECORATED_POT_RECIPE, golemItem);

      guard.on(table);
      guard.on(crafter);

      assertThat(table.result().isEmpty()).isTrue();
      assertThat(crafter.isCancelled()).describedAs("%s in a crafter", golemItem.type()).isTrue();
    }
  }

  @Test
  void plainSherdsStillCraftADecoratedPot() {
    for (Id sherd : List.of(Id.minecraft("scrape_pottery_sherd"), Id.minecraft("guster_pottery_sherd"), Id.minecraft("shelter_pottery_sherd"))) {
      PrepareItemCraftEvent table = craft(DECORATED_POT_RECIPE, GolemItemStacks.plain(Id.minecraft("brick")), GolemItemStacks.plain(sherd));
      CrafterCraftEvent crafter = crafterCraft(DECORATED_POT_RECIPE, GolemItemStacks.plain(sherd));
      ItemRef result = table.result();

      guard.on(table);
      guard.on(crafter);

      assertThat(table.result()).isSameAs(result);
      assertThat(crafter.isCancelled()).isFalse();
    }
  }

  @Test
  void crookUseIsDeniedInEitherHandWhileTheClickedBlockStillWorks() {
    for (Hand hand : new Hand[]{Hand.MAIN_HAND, Hand.OFF_HAND}) {
      PlayerInteractEvent click = interact(InteractAction.RIGHT_CLICK_BLOCK, crook(), hand);

      items.on(click);

      assertThat(click.useItem()).describedAs("%s item use", hand).isEqualTo(UseResult.DENY);
      assertThat(click.useBlock()).describedAs("%s block use", hand).isEqualTo(UseResult.ALLOW);
    }
    PlayerInteractEvent air = interact(InteractAction.RIGHT_CLICK_AIR, crook(), Hand.MAIN_HAND);
    items.on(air);
    assertThat(air.useItem()).isEqualTo(UseResult.DENY);
  }

  @Test
  void plainItemsAndLeftClicksKeepTheirVanillaUse() {
    PlayerInteractEvent plainHoe = interact(InteractAction.RIGHT_CLICK_BLOCK, GolemItemStacks.plain(Id.minecraft("iron_hoe")), Hand.MAIN_HAND);
    PlayerInteractEvent leftCrook = interact(InteractAction.LEFT_CLICK_BLOCK, crook(), Hand.MAIN_HAND);

    items.on(plainHoe);
    items.on(leftCrook);

    assertThat(plainHoe.useItem()).isEqualTo(UseResult.DEFAULT);
    assertThat(leftCrook.useItem()).isEqualTo(UseResult.DEFAULT);
  }

  @Test
  void dormantGolemClicksReachBlockPlacementSoTheDeployCanHookIt() {
    PlayerInteractEvent click = interact(InteractAction.RIGHT_CLICK_BLOCK, GolemItemStacks.tagged(Id.minecraft("iron_block"), "dormant_golem", null), Hand.MAIN_HAND);

    items.on(click);

    assertThat(click.useItem()).isEqualTo(UseResult.DEFAULT);
  }

  @Test
  void golemItemsNeverPlaceAsBlocksButDormantGolemsReachTheirPlacementListener() {
    BlockPlaceEvent dormant = place(GolemItemStacks.tagged(Id.minecraft("iron_block"), "dormant_golem", null));
    BlockPlaceEvent node = place(GolemItemStacks.tagged(Id.minecraft("brick"), "node", null));
    BlockPlaceEvent plain = place(GolemItemStacks.plain(Id.minecraft("iron_block")));

    items.on(dormant);
    items.on(node);
    items.on(plain);

    assertThat(dormant.isCancelled()).isFalse();
    assertThat(node.isCancelled()).isTrue();
    assertThat(plain.isCancelled()).isFalse();
  }

  @Test
  void theMonitorSafetyNetCancelsADormantGolemPlacementNothingElseCancelled() {
    BlockPlaceEvent dormant = place(GolemItemStacks.tagged(Id.minecraft("waxed_exposed_copper"), "dormant_golem", null));
    BlockPlaceEvent plain = place(GolemItemStacks.plain(Id.minecraft("snow_block")));

    guard.placed(dormant);
    guard.placed(plain);

    assertThat(dormant.isCancelled()).isTrue();
    assertThat(plain.isCancelled()).isFalse();
  }

  @Test
  void smithingAnvilAndGrindstoneResultsClearForGolemItems() {
    ItemRef crook = crook();
    PrepareResultEvent smithing = new PrepareResultEvent(mock(PlayerRef.class), InventoryKind.SMITHING,
        List.of(FakeItem.empty(), crook, FakeItem.empty()), FakeItem.of("netherite_hoe"));
    PrepareAnvilEvent anvilEvent = new PrepareAnvilEvent(mock(PlayerRef.class), InventoryKind.ANVIL, List.of(crook, FakeItem.empty()),
        FakeItem.of("iron_hoe"), "", 0);
    PrepareResultEvent grindstoneEvent = new PrepareResultEvent(mock(PlayerRef.class), InventoryKind.GRINDSTONE,
        List.of(crook, FakeItem.empty()), FakeItem.of("iron_hoe"));
    ItemRef plainResult = FakeItem.of("iron_hoe");
    PrepareResultEvent plainEvent = new PrepareResultEvent(mock(PlayerRef.class), InventoryKind.GRINDSTONE,
        List.of(GolemItemStacks.plain(Id.minecraft("iron_hoe")), FakeItem.empty(), crook()), plainResult);

    items.on(smithing);
    items.on(anvilEvent);
    items.on(grindstoneEvent);
    items.on(plainEvent);

    assertThat(smithing.result().isEmpty()).isTrue();
    assertThat(anvilEvent.result().isEmpty()).isTrue();
    assertThat(grindstoneEvent.result().isEmpty()).isTrue();
    assertThat(plainEvent.result()).isSameAs(plainResult);
  }

  @Test
  void enchantingTablesRefuseGolemItems() {
    PrepareItemEnchantEvent prepareCrook = prepareEnchant(crook());
    PrepareItemEnchantEvent prepareHoe = prepareEnchant(GolemItemStacks.plain(Id.minecraft("iron_hoe")));
    EnchantItemEvent enchantCrook = enchant(crook());
    EnchantItemEvent enchantHoe = enchant(GolemItemStacks.plain(Id.minecraft("iron_hoe")));

    items.on(prepareCrook);
    items.on(prepareHoe);
    items.on(enchantCrook);
    items.on(enchantHoe);

    assertThat(prepareCrook.isCancelled()).isTrue();
    assertThat(prepareHoe.isCancelled()).isFalse();
    assertThat(enchantCrook.isCancelled()).isTrue();
    assertThat(enchantHoe.isCancelled()).isFalse();
  }

  @Test
  void furnacesNeverCookGolemItems() {
    BlockCookEvent node = smelt(GolemItemStacks.tagged(Id.minecraft("clay_ball"), "blank_node", null));
    BlockCookEvent clay = smelt(GolemItemStacks.plain(Id.minecraft("clay_ball")));

    items.on(node);
    items.on(clay);

    assertThat(node.isCancelled()).isTrue();
    assertThat(clay.isCancelled()).isFalse();
  }

  @Test
  void stonecuttersCheckOnlyTheirInput() {
    ItemRef dormantCopper = GolemItemStacks.tagged(Id.minecraft("copper_block"), "dormant_golem", null);
    InventoryClickEvent taggedInput = resultClick(InventoryKind.STONECUTTER, List.of(dormantCopper, GolemItemStacks.plain(Id.minecraft("cut_copper"))), List.of());
    InventoryClickEvent taggedResultOnly = resultClick(InventoryKind.STONECUTTER, List.of(GolemItemStacks.plain(Id.minecraft("copper_block")), dormantCopper), List.of());
    InventoryClickEvent workbench = resultClick(InventoryKind.WORKBENCH, List.of(FakeItem.empty(), dormantCopper), List.of());

    items.on(taggedInput);
    items.on(taggedResultOnly);
    items.on(workbench);

    assertThat(taggedInput.isCancelled()).isTrue();
    assertThat(taggedResultOnly.isCancelled()).isFalse();
    assertThat(workbench.isCancelled()).isFalse();
  }

  @Test
  void stonecuttersReadTheirInputSlotWithoutThePaperOnlyAccessor() {
    ItemRef input = GolemItemStacks.tagged(Id.minecraft("iron_block"), "dormant_golem", null);
    InventoryClickEvent click = resultClick(InventoryKind.STONECUTTER, List.of(input, GolemItemStacks.plain(Id.minecraft("iron_bars"))), List.of());

    items.on(click);

    assertThat(click.isCancelled()).isTrue();
    assertThat(click.topItems().getFirst()).isSameAs(input);
  }

  @Test
  void merchantsRefuseOnlyTradesThatWouldSwallowAGolemItemAsAPlainItem() {
    ItemRef node = GolemItemStacks.tagged(Id.minecraft("clay_ball"), "blank_node", null);
    ItemRef plainClay = GolemItemStacks.plain(Id.minecraft("clay_ball"));
    ItemRef emerald = GolemItemStacks.plain(Id.minecraft("emerald"));
    InventoryClickEvent genericTrade = merchant(node, FakeItem.empty(), plainClay, emerald);
    InventoryClickEvent golemTrade = merchant(node, FakeItem.empty(), GolemItemStacks.tagged(Id.minecraft("clay_ball"), "blank_node", null), emerald);
    InventoryClickEvent buyingAGolemItem = merchant(emerald, FakeItem.empty(), emerald, node);
    InventoryClickEvent plainTrade = merchant(plainClay, FakeItem.empty(), plainClay, emerald);

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
    BlockDispenseEvent dispenser = new BlockDispenseEvent(block(Id.minecraft("dispenser")), crook(), Vec3.ZERO);
    BlockDispenseEvent dropper = new BlockDispenseEvent(block(Id.minecraft("dropper")), crook(), Vec3.ZERO);
    BlockDispenseEvent hoe = new BlockDispenseEvent(block(Id.minecraft("dispenser")), GolemItemStacks.plain(Id.minecraft("iron_hoe")), Vec3.ZERO);

    items.on(dispenser);
    items.on(dropper);
    items.on(hoe);

    assertThat(dispenser.isCancelled()).isTrue();
    assertThat(dropper.isCancelled()).isFalse();
    assertThat(hoe.isCancelled()).isFalse();
  }

  @Test
  void hoppersNeverPickUpShatteredCoresOrDormantGolems() {
    InventoryPickupItemEvent shattered = hopperPickup(GolemItemStacks.tagged(Id.minecraft("firework_star"), "shattered_core", null));
    InventoryPickupItemEvent dormant = hopperPickup(GolemItemStacks.tagged(Id.minecraft("iron_block"), "dormant_golem", null));
    InventoryPickupItemEvent core = hopperPickup(GolemItemStacks.tagged(Id.minecraft("firework_star"), "blank_core", null));
    InventoryPickupItemEvent plain = hopperPickup(GolemItemStacks.plain(Id.minecraft("firework_star")));

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
    try (MockedStatic<ItemArt> models = models(Map.of())) {
      FakeItem dirt = spy(GolemItemStacks.plain(Id.minecraft("dirt")));
      InventoryPickupItemEvent pickup = hopperPickup(dirt);

      guard.on(pickup);

      assertThat(pickup.isCancelled()).isFalse();
      verify(dirt, never()).isEmpty();
      verify(dirt, never()).data();
    }
  }

  @Test
  void hoppersStillCatchOwnerBoundItemsWhoseModelUsesAnotherMaterial() {
    try (MockedStatic<ItemArt> models = models(Map.of("shattered_core", Id.minecraft("echo_shard"), "dormant_golem.iron_block", Id.minecraft("lodestone")))) {
      InventoryPickupItemEvent core = hopperPickup(GolemItemStacks.tagged(Id.minecraft("echo_shard"), "shattered_core", null));
      InventoryPickupItemEvent dormant = hopperPickup(GolemItemStacks.tagged(Id.minecraft("lodestone"), "dormant_golem", null));
      InventoryPickupItemEvent shard = hopperPickup(GolemItemStacks.plain(Id.minecraft("echo_shard")));

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
    try (MockedStatic<ItemArt> models = models(Map.of("shattered_core", Id.minecraft("echo_shard")))) {
      assertThat(GolemItems.mayBeOwnerBound(Id.minecraft("echo_shard"))).isTrue();
      long reloaded = GENERATIONS.incrementAndGet();
      models.when(ItemArt::revision).thenReturn(reloaded);
      models.when(() -> ItemArt.modelled(any(Id.class), any(String[].class)))
          .thenAnswer(call -> new FakeItem(call.getArgument(0), 1));

      assertThat(GolemItems.mayBeOwnerBound(Id.minecraft("echo_shard"))).isFalse();
      assertThat(GolemItems.mayBeOwnerBound(Id.minecraft("firework_star"))).isTrue();
    }
  }

  private static MockedStatic<ItemArt> models(Map<String, Id> overrides) {
    MockedStatic<ItemArt> models = mockStatic(ItemArt.class);
    models.when(ItemArt::revision).thenReturn(GENERATIONS.incrementAndGet());
    models.when(() -> ItemArt.modelled(any(Id.class), any(String[].class))).thenAnswer(call -> {
      String[] path = (String[]) call.getRawArguments()[1];
      String key = String.join(".", Arrays.copyOfRange(path, 1, path.length));
      return new FakeItem(overrides.containsKey(key) ? overrides.get(key) : call.<Id>getArgument(0), 1);
    });
    return models;
  }

  private static InventoryPickupItemEvent hopperPickup(ItemRef stack) {
    ItemEntityRef item = mock(ItemEntityRef.class);
    when(item.item()).thenReturn(stack);
    return new InventoryPickupItemEvent(mock(InventoryRef.class), InventoryKind.HOPPER, null, item);
  }

  private static ExactIngredient spec(GolemItemKind kind, Id base) {
    return new ExactIngredient(base, (ItemRef item) -> GolemItems.kindOf(item).filter((GolemItemKind found) -> found == kind).isPresent());
  }

  private static List<ItemRef> potIngredients() {
    List<ItemRef> items = new ArrayList<>();
    items.add(GolemItemStacks.tagged(Id.minecraft("scrape_pottery_sherd"), "blank_rune", null));
    items.add(GolemItemStacks.tagged(Id.minecraft("brick"), "node", "{\"type\":\"deposit\"}"));
    Map<Id, String> aspects = Map.of(
        Id.minecraft("guster_pottery_sherd"), "swiftness",
        Id.minecraft("arms_up_pottery_sherd"), "might",
        Id.minecraft("burn_pottery_sherd"), "ember",
        Id.minecraft("angler_pottery_sherd"), "tide",
        Id.minecraft("sheaf_pottery_sherd"), "order",
        Id.minecraft("danger_pottery_sherd"), "entropy",
        Id.minecraft("shelter_pottery_sherd"), "bulwark");
    for (Map.Entry<Id, String> aspect : aspects.entrySet()) {
      items.add(GolemItemStacks.tagged(aspect.getKey(), "rune", "{\"aspect\":\"" + aspect.getValue() + "\",\"tier\":2}"));
    }
    return items;
  }

  private static ItemRef crook() {
    return GolemItemStacks.tagged(Id.minecraft("iron_hoe"), "crook", null);
  }

  private static ItemRef blankCore() {
    return GolemItemStacks.tagged(Id.minecraft("firework_star"), "blank_core", null);
  }

  private static BlockRef block(Id type) {
    BlockRef block = mock(BlockRef.class);
    when(block.type()).thenReturn(type);
    return block;
  }

  private static PlayerInteractEvent interact(InteractAction action, ItemRef item, Hand hand) {
    BlockRef block = action == InteractAction.RIGHT_CLICK_AIR ? null : mock(BlockRef.class);
    return new PlayerInteractEvent(mock(PlayerRef.class), action, hand, item, block, BlockFace.UP, null,
        block == null ? UseResult.DENY : UseResult.ALLOW, UseResult.DEFAULT);
  }

  private static BlockPlaceEvent place(ItemRef item) {
    return new BlockPlaceEvent(mock(PlayerRef.class), mock(BlockRef.class), mock(BlockStateRef.class), mock(BlockRef.class), item, Hand.MAIN_HAND);
  }

  private static PrepareItemCraftEvent craft(Id recipeKey, ItemRef... ingredients) {
    return new PrepareItemCraftEvent(mock(PlayerRef.class), List.of(ingredients), recipeKey, CraftItemEvent.RecipeKind.SHAPED, false,
        FakeItem.of("iron_ingot"));
  }

  private static CrafterCraftEvent crafterCraft(Id recipeKey, ItemRef ingredient) {
    InventoryRef contents = mock(InventoryRef.class);
    when(contents.contents()).thenReturn(Arrays.asList(ingredient, FakeItem.empty()));
    BlockRef block = mock(BlockRef.class);
    when(block.container()).thenReturn(contents);
    return new CrafterCraftEvent(block, recipeKey, FakeItem.of("decorated_pot"));
  }

  private static PrepareItemEnchantEvent prepareEnchant(ItemRef item) {
    return new PrepareItemEnchantEvent(mock(PlayerRef.class), mock(BlockRef.class), item, 0, List.of());
  }

  private static EnchantItemEvent enchant(ItemRef item) {
    return new EnchantItemEvent(mock(PlayerRef.class), mock(BlockRef.class), item, 1, new HashMap<>(), 0);
  }

  private static BlockCookEvent smelt(ItemRef source) {
    return new BlockCookEvent(mock(BlockRef.class), source, FakeItem.of("brick"));
  }

  private static InventoryClickEvent merchant(ItemRef firstInput, ItemRef secondInput, ItemRef tradeIngredient, ItemRef tradeResult) {
    return resultClick(InventoryKind.MERCHANT, List.of(firstInput, secondInput, tradeResult), List.of(tradeIngredient));
  }

  private static InventoryClickEvent resultClick(InventoryKind view, List<ItemRef> top, List<ItemRef> tradeCost) {
    InventoryClickEvent click = new InventoryClickEvent(mock(PlayerRef.class), view, view, 0, 0, SlotKind.RESULT, ClickKind.LEFT,
        InventoryAction.PICKUP_ALL, -1, FakeItem.empty(), FakeItem.empty());
    click.setViewContents(top, tradeCost);
    return click;
  }
}
