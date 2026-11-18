package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.platform.entity.PlayerRef;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodePlacementVerdict;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.api.golem.node.NodeWeavingPolicy;
import art.arcane.adapt.api.golem.plinth.PlinthService;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ExactIngredient;
import art.arcane.adapt.api.recipe.ExactIngredientBook;
import art.arcane.adapt.api.recipe.ExactItemResolver;
import art.arcane.adapt.api.recipe.RecipeItemCatalog;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemNodeWeavingExecutionTest extends AdaptTestBase {
  private static final Id BLANK_NODE_KEY = Id.adapt("golemancy-blank-node");
  private static final Id DEPOSIT_NODE_KEY = Id.adapt("golemancy-deposit-node");

  private final Map<String, Id> materials = new HashMap<>();
  private final Map<Id, ItemRef> results = new HashMap<>();
  private final ExactIngredientBook book = new ExactIngredientBook();
  private MockedStatic<ConfiguredRecipeParser> parsers;
  private MockedStatic<GolemancyRuntime> runtime;
  private GolemNodeWeaving adaptation;

  @BeforeEach
  void construct() {
    for (String name : List.of("CLAY_BALL", "REDSTONE", "AMETHYST_SHARD", "CHEST")) {
      material(name);
    }
    result("blank_node");
    result("node/deposit");
    ConfiguredRecipeParser parser = new ConfiguredRecipeParser(new FakeCatalog(), new FakeResolver(), book);
    parsers = mockStatic(ConfiguredRecipeParser.class, CALLS_REAL_METHODS);
    parsers.when(() -> ConfiguredRecipeParser.platform(GolemItems.resolver(), GolemItems.recipeBook())).thenReturn(parser);
    runtime = mockStatic(GolemancyRuntime.class);
    adaptation = new GolemNodeWeaving();
  }

  @AfterEach
  void close() {
    runtime.close();
    parsers.close();
  }

  @Test
  void nodeRevealAndTheWorkAreaOutlineArePerViewerChoicesThatDefaultToTheCurrentReveal() {
    assertThat(adaptation.getPlayerPreferences())
        .containsExactly(CommonPreferences.ENABLED, GolemancyPreferences.NODE_REVEAL, GolemancyPreferences.WORK_AREA_OUTLINE);
    assertThat(GolemancyPreferences.NODE_REVEAL.defaultValue()).isEqualTo(GolemancyPreferences.NodeReveal.ANY_ITEM);
    GolemancyPreferenceAssertions.assertVisibleFrom(adaptation, GolemancyPreferences.NODE_REVEAL, 0);
    GolemancyPreferenceAssertions.assertVisibleFrom(adaptation, GolemancyPreferences.WORK_AREA_OUTLINE, 0);
    PlayerRef viewer = mock(PlayerRef.class);
    GolemNodeWeaving learned = spy(adaptation);
    doReturn(GolemancyPreferences.NodeReveal.ANY_ITEM).when(learned).preference(viewer, GolemancyPreferences.NODE_REVEAL);
    doReturn(true).when(learned).preferenceEnabled(viewer, GolemancyPreferences.WORK_AREA_OUTLINE);

    assertThat(learned.crookOnlyReveal(viewer)).isFalse();
    assertThat(learned.hidesOutline(viewer)).isFalse();

    doReturn(GolemancyPreferences.NodeReveal.CROOK_ONLY).when(learned).preference(viewer, GolemancyPreferences.NODE_REVEAL);
    doReturn(false).when(learned).preferenceEnabled(viewer, GolemancyPreferences.WORK_AREA_OUTLINE);

    assertThat(learned.crookOnlyReveal(viewer)).isTrue();
    assertThat(learned.hidesOutline(viewer)).isTrue();
  }

  @Test
  void bothRecipesRegisterAtLevelOneUnderGolemancyKeys() {
    List<RecipeSpec> recipes = adaptation.getRecipes();

    assertThat(recipes).extracting(RecipeSpec::key).containsExactly(BLANK_NODE_KEY, DEPOSIT_NODE_KEY);
    assertThat(recipes).allMatch(recipe -> recipe.requiredLevel() == 1);
    assertThat(book.satisfiedBy(BLANK_NODE_KEY, List.of())).isTrue();
    assertThat(GolemItems.craftCounter().tracks(BLANK_NODE_KEY)).isTrue();
    assertThat(GolemItems.craftCounter().tracks(DEPOSIT_NODE_KEY)).isTrue();
  }

  @Test
  void theAdaptationRegistersItselfAsTheNodeWeavingPolicy() {
    runtime.verify(() -> GolemancyRuntime.setNodeWeaving(adaptation));
    assertThat(adaptation).isInstanceOf(NodeWeavingPolicy.class);
    assertThat(adaptation.nodeCap(3)).isEqualTo(12);
    assertThat(adaptation.maxAreaRadius(4)).isEqualTo(15);
    assertThat(adaptation.filterSlots(5)).isEqualTo(18);
  }

  @Test
  void configurationLoadsAndEveryLevelHasAPositiveCostAndProgress() {
    assertThat(adaptation.reloadConfigFromDisk(false)).isTrue();
    assertThat(adaptation.getConfig()).isNotNull();
    assertThat(adaptation.getMaxLevel()).isEqualTo(5);
    assertThat(adaptation.getConfig().requires).containsExactly("golem-animus-binding:1");
    double previous = 0D;
    for (int level = 1; level <= adaptation.getMaxLevel(); level++) {
      assertThat(adaptation.getCostFor(level)).as("cost at level %s", level).isPositive();
      assertThat(adaptation.getLevelPercent(level)).isBetween(previous, 1D);
      previous = adaptation.getLevelPercent(level);
    }
    assertThat(adaptation.isRuntimeRegistered()).isFalse();
  }

  @Test
  void everyLevelShowsItsCapsAndTheUnlocksItHasReached() {
    assertThat(lore(1)).containsExactly("+ 4 Nodes You Can Own", "+ 8 Block Work Area Radius", "+ 3 Filter Slots");
    assertThat(lore(2)).containsExactly("+ 8 Nodes You Can Own", "+ 12 Block Work Area Radius", "+ 6 Filter Slots");
    assertThat(lore(3)).containsExactly("+ 12 Nodes You Can Own", "+ 15 Block Work Area Radius", "+ 6 Filter Slots", "+ Node Priority 1-5");
    assertThat(lore(4)).containsExactly("+ 18 Nodes You Can Own", "+ 15 Block Work Area Radius", "+ 9 Filter Slots", "+ Node Priority 1-5",
        "+ Redstone Gating");
    assertThat(lore(5)).containsExactly("+ 24 Nodes You Can Own", "+ 15 Block Work Area Radius", "+ 18 Filter Slots", "+ Node Priority 1-5",
        "+ Redstone Gating", "+ Blacklist and Tag Filters");
  }

  @Test
  void aDisabledReloadTurnsTheAdaptationOff() {
    assertThat(adaptation.reloadConfigSnapshot("enabled = false\nmaxLevel = 1\n", dataFolder.toPath().resolve("disabled.toml").toFile(), false)).isTrue();
    assertThat(adaptation.isEnabled()).isFalse();
    assertThat(adaptation.getMaxLevel()).isEqualTo(1);
  }

  @Test
  void sneakRightClickingABlockFaceWithANodePlacesItAndConsumesOne() {
    NodeService service = mock(NodeService.class);
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
    Click click = click(true, Hand.MAIN_HAND, 2);
    when(service.place(click.player, click.block, BlockFace.UP, NodeType.DEPOSIT, click.adaptation)).thenReturn(NodePlacementVerdict.READY);

    click.fire();

    verify(click.event).setCancelled(true);
    verify(service).place(click.player, click.block, BlockFace.UP, NodeType.DEPOSIT, click.adaptation);
    verify(click.held).setAmount(1);
  }

  @Test
  void theFirstNodeOfEachTypePaysOnceAndLaterOnesPayNothing() {
    NodeService service = mock(NodeService.class);
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
    Click click = click(true, Hand.MAIN_HAND, 2);
    when(service.place(click.player, click.block, BlockFace.UP, NodeType.DEPOSIT, click.adaptation)).thenReturn(NodePlacementVerdict.READY);
    doNothing().when(click.adaptation).xp(any(PlayerRef.class), any(Loc.class), anyDouble(), anyString());
    doCallRealMethod().when(click.adaptation).firstNode(any(PlayerRef.class), any(NodeType.class));
    PlayerData data = new PlayerData();
    AdaptServer server = mock(AdaptServer.class);
    AdaptPlayer online = mock(AdaptPlayer.class);
    when(core.getAdaptServer()).thenReturn(server);
    when(server.getOnlineAdaptPlayer(click.player.uuid())).thenReturn(online);
    when(online.isRuntimeReady()).thenReturn(true);
    PlayerRef onlineRef = click.player;
    when(online.getPlayer()).thenReturn(onlineRef);
    when(online.getData()).thenReturn(data);

    click.fire();
    click.fire();
    click.adaptation.rewardFirstNode(click.player, NodeType.GUARD, new Loc(mock(WorldRef.class), 0D, 64D, 0D));

    verify(click.adaptation, times(1)).xp(eq(click.player), any(Loc.class), eq(200D), eq("first_node:deposit"));
    verify(click.adaptation, times(1)).xp(eq(click.player), any(Loc.class), eq(200D), eq("first_node:guard"));
    assertThat(data.getStat("golemancy.nodes.deposit")).isEqualTo(2D);
    assertThat(data.getStat("golemancy.nodes.guard")).isEqualTo(1D);
  }

  @Test
  void playerDataIsSavedAfterThePlacedNodeLeavesTheHand() {
    NodeService service = mock(NodeService.class);
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
    Click click = click(true, Hand.MAIN_HAND, 2);
    when(service.place(click.player, click.block, BlockFace.UP, NodeType.DEPOSIT, click.adaptation)).thenReturn(NodePlacementVerdict.READY);

    click.fire();

    InOrder order = inOrder(service, click.held, click.player);
    order.verify(service).place(click.player, click.block, BlockFace.UP, NodeType.DEPOSIT, click.adaptation);
    order.verify(click.held).setAmount(1);
    order.verify(click.player).saveData();
  }

  @Test
  void aRefusedPlacementSavesNoPlayerData() {
    NodeService service = mock(NodeService.class);
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
    Click click = click(true, Hand.MAIN_HAND, 2);
    when(service.place(click.player, click.block, BlockFace.UP, NodeType.DEPOSIT, click.adaptation)).thenReturn(NodePlacementVerdict.OCCUPIED);

    click.fire();

    verify(click.player, never()).saveData();
  }

  @Test
  void aValidAnimusPlinthBlockTakesPrecedenceOverNodePlacement() {
    NodeService service = mock(NodeService.class);
    PlinthService plinths = mock(PlinthService.class);
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
    runtime.when(GolemancyRuntime::plinths).thenReturn(Optional.of(plinths));
    Click main = click(true, Hand.MAIN_HAND, 2);
    Click offHand = click(true, Hand.OFF_HAND, 2);
    when(plinths.isPlinthBlock(main.block)).thenReturn(true);
    when(plinths.isPlinthBlock(offHand.block)).thenReturn(true);

    main.fire();
    offHand.fire();

    verify(main.event, never()).setCancelled(anyBoolean());
    verify(offHand.event, never()).setCancelled(anyBoolean());
    verify(service, never()).place(any(), any(), any(), any(), any());
    verify(main.held, never()).setAmount(anyInt());
  }

  @Test
  void aRefusedPlacementKeepsTheItem() {
    NodeService service = mock(NodeService.class);
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
    Click click = click(true, Hand.MAIN_HAND, 2);
    when(service.place(click.player, click.block, BlockFace.UP, NodeType.DEPOSIT, click.adaptation)).thenReturn(NodePlacementVerdict.NEEDS_CONTAINER);

    click.fire();

    verify(click.event).setCancelled(true);
    verify(click.held, never()).setAmount(1);
  }

  @Test
  void aFishingNodeOnATopFaceIsRefusedWithASideFaceMessageAndKeepsTheItem() {
    NodeService service = mock(NodeService.class);
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
    Click click = click(true, Hand.MAIN_HAND, 2);
    when(service.place(click.player, click.block, BlockFace.UP, NodeType.FISHING, click.adaptation)).thenReturn(NodePlacementVerdict.NEEDS_SIDE_FACE);
    List<Runnable> scheduled = new ArrayList<>();

    try (MockedStatic<Tasks> scheduling = mockStatic(Tasks.class);
         MockedStatic<GolemItems> items = mockStatic(GolemItems.class, CALLS_REAL_METHODS);
         MockedStatic<AdaptHud> hud = mockStatic(AdaptHud.class)) {
      scheduling.when(() -> Tasks.runEntity(any(EntityRef.class), any(Runnable.class))).thenAnswer(invocation -> scheduled.add(invocation.getArgument(1)));
      items.when(() -> GolemItems.nodeTypeOf(click.held)).thenReturn(Optional.of(NodeType.FISHING));
      click.adaptation.on(click.event);
      scheduled.forEach(Runnable::run);

      hud.verify(() -> AdaptHud.actionBar(eq(click.player), argThat(text -> text.endsWith("A Fishing Node goes on the side of a block"))));
    }
    verify(click.event).setCancelled(true);
    verify(click.held, never()).setAmount(anyInt());
  }

  @Test
  void clicksWithoutSneakingOrWithoutTheAdaptationAreLeftAlone() {
    NodeService service = mock(NodeService.class);
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
    Click standing = click(false, Hand.MAIN_HAND, 2);
    Click unlearned = click(true, Hand.MAIN_HAND, 0);

    standing.fire();
    unlearned.fire();

    verify(standing.event, never()).setCancelled(anyBoolean());
    verify(unlearned.event, never()).setCancelled(anyBoolean());
    verify(service, never()).place(any(), any(), any(), any(), any());
  }

  @Test
  void theOffHandClickAfterAPlacementIsCancelledSoItCannotAlsoPlaceABlock() {
    Click offHand = click(true, Hand.OFF_HAND, 2);

    offHand.fire();

    verify(offHand.event).setCancelled(true);
  }

  private Click click(boolean sneaking, Hand hand, int level) {
    PlayerRef player = mock(PlayerRef.class);
    PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
    ItemRef held = mock(ItemRef.class);
    lenient().when(held.amount()).thenReturn(2);
    WorldRef world = mock(WorldRef.class);
    BlockRef block = mock(BlockRef.class);
    lenient().when(block.world()).thenReturn(world);
    lenient().when(block.location()).thenReturn(new Loc(world, 1D, 64D, 1D));
    lenient().when(player.isSneaking()).thenReturn(sneaking);
    lenient().when(player.uuid()).thenReturn(UUID.randomUUID());
    lenient().when(player.inventory()).thenReturn(inventory);
    lenient().when(inventory.mainHand()).thenReturn(held);
    PlayerInteractEvent event = mock(PlayerInteractEvent.class);
    lenient().when(event.action()).thenReturn(InteractAction.RIGHT_CLICK_BLOCK);
    lenient().when(event.hand()).thenReturn(hand);
    lenient().when(event.player()).thenReturn(player);
    lenient().when(event.clickedBlock()).thenReturn(block);
    lenient().when(event.clickedFace()).thenReturn(BlockFace.UP);
    lenient().when(event.item()).thenReturn(held);
    GolemNodeWeaving learned = spy(adaptation);
    doReturn(level).when(learned).getActiveLevel(player);
    lenient().doReturn(false).when(learned).firstNode(any(PlayerRef.class), any(NodeType.class));
    return new Click(event, player, block, held, learned);
  }

  private List<String> lore(int level) {
    List<String> lines = new ArrayList<>();
    StatLines stats = new StatLines();
    adaptation.addStats(level, stats);
    for (String line : stats.lines()) {
      lines.add(line.replaceAll("§[0-9a-fk-orx]", ""));
    }
    return lines;
  }


  private void material(String name) {
    materials.put(name, Id.minecraft(name.toLowerCase(Locale.ROOT)));
  }

  private void result(String id) {
    Id type = id.equals("blank_node") ? materials.get("CLAY_BALL") : Id.minecraft("brick");
    results.put(Id.of("golemancy", id), item(type, 1));
  }

  private static ItemRef item(Id type, int amount) {
    ItemRef item = mock(ItemRef.class);
    when(item.type()).thenReturn(type);
    when(item.amount()).thenReturn(amount);
    when(item.maxStackSize()).thenReturn(64);
    when(item.copyWithAmount(anyInt())).thenAnswer(invocation -> item(type, invocation.<Integer>getArgument(0)));
    return item;
  }

  private record Click(PlayerInteractEvent event, PlayerRef player, BlockRef block, ItemRef held, GolemNodeWeaving adaptation) {
    private void fire() {
      try (MockedStatic<Tasks> scheduling = mockStatic(Tasks.class);
           MockedStatic<GolemItems> items = mockStatic(GolemItems.class, CALLS_REAL_METHODS)) {
        scheduling.when(() -> Tasks.runEntity(any(EntityRef.class), any(Runnable.class))).thenReturn(true);
        items.when(() -> GolemItems.nodeTypeOf(held)).thenReturn(Optional.of(NodeType.DEPOSIT));
        adaptation.on(event);
      }
    }
  }

  private final class FakeCatalog implements RecipeItemCatalog {
    @Override
    public Id itemMaterial(String name) {
      return materials.get(name);
    }

    @Override
    public Set<Id> itemTag(Id key) {
      return null;
    }

    @Override
    public ItemRef item(Id type, int amount) {
      return GolemNodeWeavingExecutionTest.item(type, amount);
    }

    @Override
    public int maxStackSize(Id type) {
      return 64;
    }
  }

  private final class FakeResolver implements ExactItemResolver {
    @Override
    public ExactIngredient ingredient(Id id) {
      return new ExactIngredient(materials.get("CLAY_BALL"), item -> true);
    }

    @Override
    public ItemRef result(Id id) {
      return results.get(id);
    }
  }
}
