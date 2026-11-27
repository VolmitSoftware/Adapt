package art.arcane.adapt.engine.item;

import art.arcane.adapt.content.item.BackpackItem;
import art.arcane.adapt.content.item.BoundEnderPearl;
import art.arcane.adapt.content.item.BoundEyeOfEnder;
import art.arcane.adapt.content.item.BoundRedstoneTorch;
import art.arcane.adapt.content.item.BoundSnowBall;
import art.arcane.adapt.content.item.ChronoTimeBombItem;
import art.arcane.adapt.content.item.ChronoTimeBottle;
import art.arcane.adapt.api.addon.AdaptAddon;
import art.arcane.adapt.api.addon.ContentRegistrar;
import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.item.VanillaUse;
import art.arcane.adapt.content.golem.item.GolemItemKind;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.engine.mutation.tool.MutationTools;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.content.adaptation.hunter.HunterSnares;
import art.arcane.adapt.content.item.ChalkWandItem;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.block.BlockCookEvent;
import art.arcane.adapt.platform.event.block.BlockDispenseEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.inventory.BrewEvent;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.InventoryMoveItemEvent;
import art.arcane.adapt.platform.event.inventory.PrepareAnvilEvent;
import art.arcane.adapt.platform.event.inventory.PrepareItemCraftEvent;
import art.arcane.adapt.platform.event.inventory.PrepareResultEvent;
import art.arcane.adapt.platform.event.inventory.SlotKind;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerItemConsumeEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeEntity;
import art.arcane.adapt.testkit.FakeInventory;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every item Adapt makes, against every vanilla interaction that would place, spend or transform it: each one is
 * refused unless the item's definition declares it. The item list is built from the registries and a scan of the
 * compiled classes, so an item added later is checked without touching this test.
 */
class AdaptItemSinkTest {
  private static final Id VANILLA_RECIPE = Id.minecraft("chest");
  private static final DataKey<String> GOLEM_KIND = DataKey.of(Id.of("adapt", "golem_item"), DataCodecs.STRING);

  @TempDir
  static Path data;

  private static TestPlatform platform;
  private static TestAdapt adapt;
  private static FakePlayer player;
  private static final AdaptItemGuard GUARD = new AdaptItemGuard();

  @BeforeAll
  static void start() {
    platform = TestPlatform.builder(data).build();
    adapt = TestAdapt.start(platform, new BuiltinAddon(), new StoneAddon());
    adapt.awaitTicks(adapt.core().getAdaptServer().getSkillRegistry()::isRecipeRegistrationReady, 10_000L);
    player = platform.player("Sink", 0.5D, 64D, 0.5D);
    platform.world().set(0, 63, 0, "stone");
    platform.world().set(2, 63, 0, "composter");
    platform.world().set(4, 63, 0, "dispenser");
    platform.world().set(6, 63, 0, "dropper");
    platform.world().set(8, 63, 0, "furnace");
  }

  @AfterAll
  static void stop() {
    adapt.close();
  }

  /** One vanilla interaction: what it needs declared, and whether the guard let it through for this stack. */
  private record Sink(String name, VanillaUse needs, Predicate<ItemRef> passes) {
  }

  private static List<Sink> sinks() {
    BlockRef ground = platform.world().blockAt(0, 63, 0);
    BlockRef composter = platform.world().blockAt(2, 63, 0);
    BlockRef dispenser = platform.world().blockAt(4, 63, 0);
    BlockRef furnace = platform.world().blockAt(8, 63, 0);
    return List.of(
        new Sink("place", VanillaUse.PLACE, (ItemRef item) -> !post(new BlockPlaceEvent(player, ground.relative(BlockFace.UP),
            ground.relative(BlockFace.UP).state(), ground, item, Hand.MAIN_HAND)).isCancelled()),
        new Sink("place from the off hand", VanillaUse.PLACE, (ItemRef item) -> !post(new BlockPlaceEvent(player,
            ground.relative(BlockFace.UP), ground.relative(BlockFace.UP).state(), ground, item, Hand.OFF_HAND)).isCancelled()),
        new Sink("use on a block", VanillaUse.USE, (ItemRef item) -> click(item, ground).useItem() != UseResult.DENY),
        new Sink("use in the air", VanillaUse.USE, (ItemRef item) -> post(new PlayerInteractEvent(player,
            InteractAction.RIGHT_CLICK_AIR, Hand.MAIN_HAND, item, null, null, null, UseResult.DEFAULT, UseResult.DEFAULT))
            .useItem() != UseResult.DENY),
        new Sink("compost by hand", VanillaUse.INSERT, (ItemRef item) -> click(item, composter).useBlock() != UseResult.DENY),
        new Sink("compost from a hopper", VanillaUse.INSERT, (ItemRef item) -> !post(new InventoryMoveItemEvent(
            new FakeInventory(), InventoryKind.HOPPER, null, new FakeInventory(), InventoryKind.OTHER, composter, item, true))
            .isCancelled()),
        new Sink("dispense", VanillaUse.DISPENSE, (ItemRef item) -> !post(new BlockDispenseEvent(dispenser, item, Vec3.ZERO))
            .isCancelled()),
        new Sink("eat or drink", VanillaUse.CONSUME, (ItemRef item) -> !post(new PlayerItemConsumeEvent(player, item,
            Hand.MAIN_HAND, null)).isCancelled()),
        new Sink("smelt", VanillaUse.FURNACE, (ItemRef item) -> !post(new BlockCookEvent(furnace, item, FakeItem.of("charcoal")))
            .isCancelled()),
        new Sink("fuel by hand", VanillaUse.FURNACE, (ItemRef item) -> !post(furnaceClick(item)).isCancelled()),
        new Sink("fuel from a hopper", VanillaUse.FURNACE, (ItemRef item) -> !post(new InventoryMoveItemEvent(
            new FakeInventory(), InventoryKind.HOPPER, null, new FakeInventory(), InventoryKind.FURNACE, furnace, item, true))
            .isCancelled()),
        new Sink("anvil", VanillaUse.WORKSTATION, (ItemRef item) -> !post(new PrepareAnvilEvent(player, InventoryKind.ANVIL,
            List.of(item, FakeItem.of("iron_ingot")), FakeItem.of("iron_ingot"), "", 1)).result().isEmpty()),
        new Sink("grindstone", VanillaUse.WORKSTATION, (ItemRef item) -> !post(new PrepareResultEvent(player,
            InventoryKind.GRINDSTONE, List.of(item, FakeItem.empty()), FakeItem.of("stick"))).result().isEmpty()),
        new Sink("smithing", VanillaUse.WORKSTATION, (ItemRef item) -> !post(new PrepareResultEvent(player,
            InventoryKind.SMITHING, List.of(FakeItem.empty(), item, FakeItem.empty()), FakeItem.of("stick"))).result().isEmpty()),
        new Sink("stonecutter", VanillaUse.WORKSTATION, (ItemRef item) -> !post(resultClick(InventoryKind.STONECUTTER, item))
            .isCancelled()),
        new Sink("cartography", VanillaUse.WORKSTATION, (ItemRef item) -> !post(resultClick(InventoryKind.CARTOGRAPHY, item))
            .isCancelled()),
        new Sink("brewing", VanillaUse.WORKSTATION, (ItemRef item) -> !post(new BrewEvent(furnace, item,
            List.of(FakeItem.of("potion")), List.of(FakeItem.of("potion")), 20, 400)).isCancelled()),
        new Sink("trade", VanillaUse.TRADE, (ItemRef item) -> !post(resultClick(InventoryKind.MERCHANT, item)).isCancelled()),
        new Sink("item frame", VanillaUse.ENTITY, (ItemRef item) -> {
          player.holding(item);
          return !post(new PlayerInteractEntityEvent(player, new FakeEntity(Id.minecraft("item_frame"),
              new Loc(platform.world(), 1D, 64D, 1D)), Hand.MAIN_HAND, null)).isCancelled();
        }));
  }

  @Test
  void everyAdaptItemIsRefusedByEveryVanillaSinkItDoesNotDeclare() {
    Map<String, ItemRef> items = everyAdaptItem();
    List<String> wrong = new ArrayList<>();
    for (Map.Entry<String, ItemRef> entry : items.entrySet()) {
      CustomItem.Definition<?> definition = AdaptItems.of(entry.getValue());
      assertThat(definition).as(entry.getKey()).isNotNull();
      for (Sink sink : sinks()) {
        boolean passed = sink.passes().test(entry.getValue().copy());
        if (passed != definition.allows(entry.getValue(), sink.needs())) {
          wrong.add(entry.getKey() + " / " + sink.name() + (passed ? " went through" : " was refused although declared"));
        }
      }
      boolean crafted = !post(new PrepareItemCraftEvent(player, List.of(entry.getValue().copy()), VANILLA_RECIPE,
          CraftItemEvent.RecipeKind.SHAPED, false, FakeItem.of("chest"))).result().isEmpty();
      if (crafted) {
        wrong.add(entry.getKey() + " / vanilla recipe went through");
      }
    }
    assertThat(wrong).isEmpty();
  }

  @Test
  void theItemListCoversEveryToolGolemItemAndDataBoundItemType() {
    Map<String, ItemRef> items = everyAdaptItem();
    SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();

    assertThat(MutationTools.all(registry)).hasSizeGreaterThanOrEqualTo(21);
    assertThat(items.keySet()).filteredOn((String key) -> key.startsWith("tool "))
        .hasSize(MutationTools.all(registry).size());
    assertThat(items.keySet()).filteredOn((String key) -> key.startsWith("golem "))
        .hasSize(GolemItemKind.values().length);
    assertThat(items.keySet()).filteredOn((String key) -> key.startsWith("bound "))
        .hasSize(dataBoundItems().size() + storedItems().size());
    assertThat(dataBoundItems().size() + storedItems().size()).isGreaterThanOrEqualTo(9);
  }

  @Test
  void anItemOtherContentRegistersIsGuardedLikeTheBuiltIns() {
    FakeItem stone = FakeItem.of("gold_nugget");
    stone.data().set(StoneAddon.MARKER, "stone");

    CustomItem.Definition<?> definition = AdaptItems.of(stone);
    assertThat(definition).isNotNull();
    for (Sink sink : sinks()) {
      assertThat(sink.passes().test(stone.copy())).as(sink.name()).isEqualTo(definition.allows(stone, sink.needs()));
    }
    assertThat(AdaptItems.refusedBy(VANILLA_RECIPE, List.of(stone))).isTrue();
    assertThat(AdaptItems.refusedBy(Id.of("alchemist", "transmutation"), List.of(stone))).isFalse();
  }

  @Test
  void plainVanillaStacksOfTheSameMaterialsKeepEveryVanillaUse() {
    for (String material : List.of("hay_block", "conduit", "lodestone", "chest", "stick", "ender_pearl", "snowball",
        "iron_block", "tripwire_hook")) {
      ItemRef plain = FakeItem.of(material);
      assertThat(AdaptItems.of(plain)).as(material).isNull();
      for (Sink sink : sinks()) {
        assertThat(sink.passes().test(plain.copy())).as(material + " / " + sink.name()).isTrue();
      }
    }
  }

  private static Map<String, ItemRef> everyAdaptItem() {
    Map<String, ItemRef> items = new LinkedHashMap<>();
    for (MutationTool<?> tool : MutationTools.all(adapt.core().getAdaptServer().getSkillRegistry())) {
      List<RecipeSpec> recipes = tool.recipes();
      assertThat(recipes).as(tool.item().id() + " has a recipe whose result is the tool").isNotEmpty();
      items.put("tool " + tool.item().id(), recipes.getFirst().result());
    }
    for (GolemItemKind kind : GolemItemKind.values()) {
      FakeItem golem = FakeItem.of("firework_star");
      golem.data().set(GOLEM_KIND, kind.id());
      items.put("golem " + kind.id(), golem);
    }
    for (DataBoundItem<?> bound : dataBoundItems()) {
      ItemRef stack = bound.blank();
      stack.data().set(bound.dataKey(), "{}");
      items.put("bound " + bound.getClass().getSimpleName(), stack);
    }
    for (CustomItem.Definition<?> stored : storedItems()) {
      FakeItem stack = new FakeItem(stored.baseItem(), 1);
      stack.data().set(DataKey.of(stored.dataKey(), DataCodecs.STRING), "{}");
      items.put("bound " + stored.id(), stack);
    }
    for (ChalkWandItem.Tool chalk : ChalkWandItem.Tool.values()) {
      items.put("chalk " + chalk.name(), ChalkWandItem.create(chalk));
    }
    items.put("snare line", HunterSnares.buildSnareItem(1));
    return items;
  }

  /** The built-in items that keep a JSON object under a pinned data key. */
  private static List<CustomItem.Definition<?>> storedItems() {
    return List.of(BackpackItem.ITEM, BoundEnderPearl.ITEM, BoundEyeOfEnder.ITEM, BoundRedstoneTorch.ITEM,
        BoundSnowBall.ITEM, ChronoTimeBombItem.ITEM, ChronoTimeBottle.ITEM);
  }

  /** The shared instance of every concrete {@link DataBoundItem} class in the engine and built-in content classes. */
  private static List<DataBoundItem<?>> dataBoundItems() {
    try {
      List<String> names = new ArrayList<>();
      for (Class<?> owner : List.of(DataBoundItem.class, BuiltinAddon.class)) {
        Path root = Path.of(owner.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (Files.isDirectory(root)) {
          try (Stream<Path> files = Files.walk(root)) {
            files.map((Path file) -> root.relativize(file).toString()).forEach(names::add);
          }
        } else {
          try (JarFile jar = new JarFile(root.toFile())) {
            jar.stream().map(JarEntry::getName).forEach(names::add);
          }
        }
      }
      List<DataBoundItem<?>> found = new ArrayList<>();
      for (String entry : names) {
        if (!entry.endsWith(".class") || !entry.startsWith("art/arcane/adapt/")) {
          continue;
        }
        String name = entry.substring(0, entry.length() - ".class".length()).replace('/', '.');
        Class<?> type = Class.forName(name, false, DataBoundItem.class.getClassLoader());
        if (DataBoundItem.class.isAssignableFrom(type) && !type.isInterface() && !Modifier.isAbstract(type.getModifiers())) {
          found.add(sharedInstance(type));
        }
      }
      return found;
    } catch (IOException | URISyntaxException | ClassNotFoundException failure) {
      throw new AssertionError(failure);
    }
  }

  private static DataBoundItem<?> sharedInstance(Class<?> type) {
    for (Field field : type.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers()) && field.getType() == type) {
        try {
          return (DataBoundItem<?>) field.get(null);
        } catch (IllegalAccessException failure) {
          throw new AssertionError(type.getName(), failure);
        }
      }
    }
    throw new AssertionError(type.getName() + " has no shared instance");
  }

  private static PlayerInteractEvent click(ItemRef item, BlockRef block) {
    return post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND, item, block, BlockFace.UP,
        block.center().position(), UseResult.DEFAULT, UseResult.DEFAULT));
  }

  private static InventoryClickEvent furnaceClick(ItemRef cursor) {
    return new InventoryClickEvent(player, InventoryKind.FURNACE, InventoryKind.FURNACE, 1, 1, SlotKind.FUEL, ClickKind.LEFT,
        InventoryAction.PLACE_ALL, -1, FakeItem.empty(), cursor);
  }

  private static InventoryClickEvent resultClick(InventoryKind view, ItemRef input) {
    InventoryClickEvent click = new InventoryClickEvent(player, view, view, 2, 2, SlotKind.RESULT, ClickKind.LEFT,
        InventoryAction.PICKUP_ALL, -1, FakeItem.of("stick"), FakeItem.empty());
    click.setViewContents(List.of(input, FakeItem.empty(), FakeItem.of("stick")), List.of(input, FakeItem.empty()));
    return click;
  }

  private static <E> E post(E event) {
    switch (event) {
      case BlockPlaceEvent e -> GUARD.on(e);
      case PlayerInteractEvent e -> GUARD.on(e);
      case PlayerInteractEntityEvent e -> GUARD.on(e);
      case PlayerItemConsumeEvent e -> GUARD.on(e);
      case BlockDispenseEvent e -> GUARD.on(e);
      case BlockCookEvent e -> GUARD.on(e);
      case InventoryMoveItemEvent e -> GUARD.on(e);
      case PrepareItemCraftEvent e -> GUARD.on(e);
      case PrepareAnvilEvent e -> GUARD.on(e);
      case PrepareResultEvent e -> GUARD.on(e);
      case BrewEvent e -> GUARD.on(e);
      case InventoryClickEvent e -> GUARD.on(e);
      default -> throw new IllegalArgumentException(event.getClass().getName());
    }
    return event;
  }

  /** Foreign content with one guarded item, registered the way any addon registers it. */
  private static final class StoneAddon implements AdaptAddon {
    private static final DataKey<String> MARKER = DataKey.of(Id.of("alchemist", "philosophers_stone"), DataCodecs.STRING);

    @Override
    public String namespace() {
      return "alchemist";
    }

    @Override
    public void register(ContentRegistrar registrar) {
      registrar.item(CustomItem.builder(Id.of("alchemist", "philosophers_stone"), Id.minecraft("gold_nugget"))
          .recognisedBy((ItemRef item) -> item.data().has(MARKER)).allow(VanillaUse.USE).build());
    }
  }
}
