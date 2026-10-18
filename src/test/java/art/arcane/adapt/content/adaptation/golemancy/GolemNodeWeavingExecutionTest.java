package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodePlacementVerdict;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.api.golem.node.NodeWeavingPolicy;
import art.arcane.adapt.api.golem.plinth.PlinthService;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ExactIngredient;
import art.arcane.adapt.api.recipe.ExactIngredientBook;
import art.arcane.adapt.api.recipe.ExactItemResolver;
import art.arcane.adapt.api.recipe.RecipeItemCatalog;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
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
  private static final NamespacedKey BLANK_NODE_KEY = new NamespacedKey("adapt", "golemancy-blank-node");
  private static final NamespacedKey DEPOSIT_NODE_KEY = new NamespacedKey("adapt", "golemancy-deposit-node");

  private final Map<String, Material> materials = new HashMap<>();
  private final Map<NamespacedKey, ItemStack> results = new HashMap<>();
  private final ExactIngredientBook book = new ExactIngredientBook();
  private MockedStatic<ConfiguredRecipeParser> parsers;
  private MockedStatic<GolemancyRuntime> runtime;
  private GolemNodeWeaving adaptation;

  @BeforeEach
  void construct() {
    when(plugin.namespace()).thenReturn("adapt");
    for (String name : List.of("CLAY_BALL", "REDSTONE", "AMETHYST_SHARD", "CHEST")) {
      material(name);
    }
    result("blank_node");
    result("node/deposit");
    ConfiguredRecipeParser parser = new ConfiguredRecipeParser(new FakeCatalog(), new FakeResolver(), book);
    parsers = mockStatic(ConfiguredRecipeParser.class, CALLS_REAL_METHODS);
    parsers.when(() -> ConfiguredRecipeParser.bukkit(GolemItems.resolver(), GolemItems.recipeBook())).thenReturn(parser);
    runtime = mockStatic(GolemancyRuntime.class);
    adaptation = new GolemNodeWeaving();
  }

  @AfterEach
  void close() {
    runtime.close();
    parsers.close();
  }

  @Test
  void bothRecipesRegisterAtLevelOneUnderGolemancyKeysWithoutTithes() {
    List<AdaptRecipe> recipes = adaptation.getRecipes();

    assertThat(recipes).extracting(AdaptRecipe::getNSKey).containsExactly(BLANK_NODE_KEY, DEPOSIT_NODE_KEY);
    assertThat(recipes).allMatch(recipe -> recipe.getRequiredLevel() == 1);
    assertThat(book.satisfiedBy(BLANK_NODE_KEY, List.of())).isTrue();
    assertThat(GolemItems.recipeTithe().levels(keyed(BLANK_NODE_KEY))).isZero();
    assertThat(GolemItems.recipeTithe().levels(keyed(DEPOSIT_NODE_KEY))).isZero();
  }

  @Test
  void theAdaptationRegistersItselfAsTheNodeWeavingPolicy() {
    runtime.verify(() -> GolemancyRuntime.setNodeWeaving(adaptation));
    assertThat(adaptation).isInstanceOf(NodeWeavingPolicy.class);
    assertThat(adaptation.nodeCap(3)).isEqualTo(12);
    assertThat(adaptation.maxAreaRadius(4)).isEqualTo(6);
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
    assertThat(lore(1)).containsExactly("+ 4 Nodes You Can Own", "+ 2 Block Work Area Radius", "+ 3 Filter Slots");
    assertThat(lore(2)).containsExactly("+ 8 Nodes You Can Own", "+ 3 Block Work Area Radius", "+ 6 Filter Slots");
    assertThat(lore(3)).containsExactly("+ 12 Nodes You Can Own", "+ 4 Block Work Area Radius", "+ 6 Filter Slots", "+ Node Priority 1-5");
    assertThat(lore(4)).containsExactly("+ 18 Nodes You Can Own", "+ 6 Block Work Area Radius", "+ 9 Filter Slots", "+ Node Priority 1-5",
        "+ Redstone Gating");
    assertThat(lore(5)).containsExactly("+ 24 Nodes You Can Own", "+ 8 Block Work Area Radius", "+ 18 Filter Slots", "+ Node Priority 1-5",
        "+ Redstone Gating", "+ Blacklist and Tag Filters");
  }

  @Test
  void aDisabledReloadTurnsTheAdaptationOff() {
    assertThat(adaptation.reloadConfigSnapshot("enabled = false\nmaxLevel = 1\n", dataFolder.toPath().resolve("disabled.toml").toFile(), false)).isTrue();
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
      assertThat(adaptation.isEnabled()).isFalse();
    }
    assertThat(adaptation.getMaxLevel()).isEqualTo(1);
  }

  @Test
  void sneakRightClickingABlockFaceWithANodePlacesItAndConsumesOne() {
    NodeService service = mock(NodeService.class);
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
    Click click = click(true, EquipmentSlot.HAND, 2);
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
    Click click = click(true, EquipmentSlot.HAND, 2);
    when(service.place(click.player, click.block, BlockFace.UP, NodeType.DEPOSIT, click.adaptation)).thenReturn(NodePlacementVerdict.READY);
    doNothing().when(click.adaptation).xp(any(Player.class), any(Location.class), anyDouble(), anyString());
    doReturn(true, false).when(click.adaptation).firstNode(click.player, NodeType.DEPOSIT);

    click.fire();
    click.fire();

    verify(click.adaptation, times(1)).xp(eq(click.player), any(Location.class), eq(200D), eq("first_node:deposit"));
  }

  @Test
  void playerDataIsSavedAfterThePlacedNodeLeavesTheHand() {
    NodeService service = mock(NodeService.class);
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
    Click click = click(true, EquipmentSlot.HAND, 2);
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
    Click click = click(true, EquipmentSlot.HAND, 2);
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
    Click main = click(true, EquipmentSlot.HAND, 2);
    Click offHand = click(true, EquipmentSlot.OFF_HAND, 2);
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
    Click click = click(true, EquipmentSlot.HAND, 2);
    when(service.place(click.player, click.block, BlockFace.UP, NodeType.DEPOSIT, click.adaptation)).thenReturn(NodePlacementVerdict.NEEDS_CONTAINER);

    click.fire();

    verify(click.event).setCancelled(true);
    verify(click.held, never()).setAmount(1);
  }

  @Test
  void aFishingNodeOnATopFaceIsRefusedWithASideFaceMessageAndKeepsTheItem() {
    NodeService service = mock(NodeService.class);
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
    Click click = click(true, EquipmentSlot.HAND, 2);
    when(service.place(click.player, click.block, BlockFace.UP, NodeType.FISHING, click.adaptation)).thenReturn(NodePlacementVerdict.NEEDS_SIDE_FACE);
    List<Runnable> scheduled = new ArrayList<>();

    try (MockedStatic<J> scheduling = mockStatic(J.class);
         MockedStatic<GolemItems> items = mockStatic(GolemItems.class, CALLS_REAL_METHODS);
         MockedStatic<AdaptHud> hud = mockStatic(AdaptHud.class)) {
      scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class))).thenAnswer(invocation -> scheduled.add(invocation.getArgument(1)));
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
    Click standing = click(false, EquipmentSlot.HAND, 2);
    Click unlearned = click(true, EquipmentSlot.HAND, 0);

    standing.fire();
    unlearned.fire();

    verify(standing.event, never()).setCancelled(anyBoolean());
    verify(unlearned.event, never()).setCancelled(anyBoolean());
    verify(service, never()).place(any(), any(), any(), any(), any());
  }

  @Test
  void theOffHandClickAfterAPlacementIsCancelledSoItCannotAlsoPlaceABlock() {
    Click offHand = click(true, EquipmentSlot.OFF_HAND, 2);

    offHand.fire();

    verify(offHand.event).setCancelled(true);
  }

  private Click click(boolean sneaking, EquipmentSlot hand, int level) {
    Player player = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    ItemStack held = mock(ItemStack.class);
    lenient().when(held.getAmount()).thenReturn(2);
    World world = mock(World.class);
    Block block = mock(Block.class);
    lenient().when(block.getWorld()).thenReturn(world);
    lenient().when(block.getLocation()).thenReturn(new Location(world, 1D, 64D, 1D));
    lenient().when(player.isSneaking()).thenReturn(sneaking);
    lenient().when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    lenient().when(player.getInventory()).thenReturn(inventory);
    lenient().when(inventory.getItemInMainHand()).thenReturn(held);
    PlayerInteractEvent event = mock(PlayerInteractEvent.class);
    lenient().when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
    lenient().when(event.getHand()).thenReturn(hand);
    lenient().when(event.getPlayer()).thenReturn(player);
    lenient().when(event.getClickedBlock()).thenReturn(block);
    lenient().when(event.getBlockFace()).thenReturn(BlockFace.UP);
    lenient().when(event.getItem()).thenReturn(held);
    GolemNodeWeaving learned = spy(adaptation);
    doReturn(level).when(learned).getActiveLevel(player);
    lenient().doReturn(false).when(learned).firstNode(any(Player.class), any(NodeType.class));
    return new Click(event, player, block, held, learned);
  }

  private List<String> lore(int level) {
    List<String> lines = new ArrayList<>();
    Element stats = mock(Element.class, RETURNS_SELF);
    when(stats.addLore(anyString())).thenAnswer(invocation -> {
      lines.add(invocation.<String>getArgument(0).replaceAll("§[0-9a-fk-orx]", ""));
      return stats;
    });
    adaptation.addStats(level, stats);
    return lines;
  }

  private static ShapedRecipe keyed(NamespacedKey key) {
    ShapedRecipe recipe = mock(ShapedRecipe.class);
    when(recipe.getKey()).thenReturn(key);
    return recipe;
  }

  private void material(String name) {
    Material material = mock(Material.class);
    when(material.isItem()).thenReturn(true);
    when(material.name()).thenReturn(name);
    materials.put(name, material);
  }

  private void result(String id) {
    ItemStack template = mock(ItemStack.class);
    ItemStack copy = mock(ItemStack.class);
    when(template.clone()).thenReturn(copy);
    results.put(new NamespacedKey("golemancy", id), template);
  }

  private record Click(PlayerInteractEvent event, Player player, Block block, ItemStack held, GolemNodeWeaving adaptation) {
    private void fire() {
      try (MockedStatic<J> scheduling = mockStatic(J.class);
           MockedStatic<GolemItems> items = mockStatic(GolemItems.class, CALLS_REAL_METHODS)) {
        scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class))).thenReturn(true);
        items.when(() -> GolemItems.nodeTypeOf(held)).thenReturn(Optional.of(NodeType.DEPOSIT));
        adaptation.on(event);
      }
    }
  }

  private final class FakeCatalog implements RecipeItemCatalog {
    @Override
    public Material itemMaterial(String name) {
      return materials.get(name);
    }

    @Override
    public Tag<Material> itemTag(NamespacedKey key) {
      return null;
    }
  }

  private final class FakeResolver implements ExactItemResolver {
    @Override
    public ExactIngredient ingredient(NamespacedKey id) {
      return new ExactIngredient(materials.get("CLAY_BALL"), item -> true);
    }

    @Override
    public ItemStack result(NamespacedKey id) {
      return results.get(id);
    }
  }
}
