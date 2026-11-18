package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemBrainSettings;
import art.arcane.adapt.api.golem.GolemSettings;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ExactIngredient;
import art.arcane.adapt.api.recipe.ExactIngredientBook;
import art.arcane.adapt.api.recipe.ExactItemResolver;
import art.arcane.adapt.api.recipe.RecipeItemCatalog;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.AnnotatedHandlers;
import art.arcane.adapt.platform.event.block.CrafterCraftEvent;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.PrepareItemCraftEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GolemancyFreeCraftingTest extends AdaptTestBase {
  private static final Id BLANK_CORE_KEY = Id.of("adapt", "golemancy-blank-core");
  private static final Id GUARDIAN_CORE_KEY = Id.of("adapt", "golemancy-guardian-core");

  private final Map<String, Id> materials = new HashMap<>();
  private final Map<Id, ItemRef> results = new HashMap<>();
  private final ExactIngredientBook book = new ExactIngredientBook();
  private final AdaptEventBus bus = new AdaptEventBus((owner, event, error) -> {
    throw new AssertionError("handler failed: " + owner, error);
  });
  private MockedStatic<ConfiguredRecipeParser> parsers;
  private MockedStatic<GolemancyRuntime> runtime;
  private PlayerRef player;

  @BeforeEach
  void construct() {
    for (String name : List.of("IRON_HOE", "AMETHYST_SHARD", "REDSTONE", "COPPER_INGOT", "ENDER_PEARL", "IRON_SWORD", "IRON_INGOT",
        "SHIELD", "FIREWORK_STAR", "CLAY_BALL")) {
      material(name);
    }
    for (String id : List.of("crook", "blank_core", "blank_node", "core/guardian", "node/guard")) {
      result(id);
    }
    ConfiguredRecipeParser parser = new ConfiguredRecipeParser(new FakeCatalog(), new FakeResolver(), book);
    parsers = mockStatic(ConfiguredRecipeParser.class, CALLS_REAL_METHODS);
    parsers.when(() -> ConfiguredRecipeParser.platform(GolemItems.resolver(), GolemItems.recipeBook())).thenReturn(parser);
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::settings).thenReturn(GolemSettings.defaults());
    runtime.when(GolemancyRuntime::brainSettings).thenReturn(GolemBrainSettings.defaults());

    player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000048"));
    when(player.level()).thenReturn(0);
    when(player.gameMode()).thenReturn(GameMode.SURVIVAL);
    when(player.ticksLived()).thenReturn(100);
    AnnotatedHandlers.register(bus, GolemItems.craftCounter());
  }

  @AfterEach
  void close() {
    bus.unregisterAll(GolemItems.craftCounter());
    runtime.close();
    parsers.close();
  }

  @Test
  void aPlayerWithNoLevelsSeesTheBlankAnimusCoreResult() {
    GolemAnimusBinding binding = new GolemAnimusBinding();

    assertThat(binding.getRecipes()).extracting(RecipeSpec::key).contains(BLANK_CORE_KEY);
    assertThat(prepare(BLANK_CORE_KEY)).isTrue();
  }

  @Test
  void aPlayerWithNoLevelsSeesARoleCoreResult() {
    GolemGuardianCore guardian = new GolemGuardianCore();

    assertThat(guardian.getRecipes()).extracting(RecipeSpec::key).contains(GUARDIAN_CORE_KEY);
    assertThat(prepare(GUARDIAN_CORE_KEY)).isTrue();
  }

  @Test
  void aPlayerWithNoLevelsMayTakeTheBlankAnimusCoreAndARoleCore() {
    new GolemAnimusBinding();
    new GolemGuardianCore();

    assertThat(click(BLANK_CORE_KEY).isCancelled()).isFalse();
    assertThat(click(GUARDIAN_CORE_KEY).isCancelled()).isFalse();
  }

  @Test
  void crafterBlocksCraftTheBlankAnimusCoreAndARoleCore() {
    new GolemAnimusBinding();
    new GolemGuardianCore();

    CrafterCraftEvent blank = bus.post(new CrafterCraftEvent(mock(BlockRef.class), BLANK_CORE_KEY, FakeItem.of("firework_star")));
    CrafterCraftEvent guardian = bus.post(new CrafterCraftEvent(mock(BlockRef.class), GUARDIAN_CORE_KEY, FakeItem.of("firework_star")));

    assertThat(blank.isCancelled()).isFalse();
    assertThat(guardian.isCancelled()).isFalse();
  }

  private boolean prepare(Id key) {
    PrepareItemCraftEvent event = bus.post(new PrepareItemCraftEvent(player, matrix(), key, CraftItemEvent.RecipeKind.SHAPED, false,
        FakeItem.of("firework_star")));
    return event.result() != null && !event.result().isEmpty();
  }

  private CraftItemEvent click(Id key) {
    return bus.post(new CraftItemEvent(player, InventoryKind.WORKBENCH, 0, 0, ClickKind.LEFT, InventoryAction.PICKUP_ALL, -1,
        FakeItem.of("firework_star"), FakeItem.empty(), key, CraftItemEvent.RecipeKind.SHAPED, matrix()));
  }

  private static List<ItemRef> matrix() {
    List<ItemRef> matrix = new ArrayList<>(9);
    for (int slot = 0; slot < 9; slot++) {
      matrix.add(FakeItem.empty());
    }
    return matrix;
  }

  private void material(String name) {
    materials.put(name, Id.minecraft(name.toLowerCase(Locale.ROOT)));
  }

  private void result(String id) {
    Id material = materials.get(id.contains("node") ? "CLAY_BALL" : "FIREWORK_STAR");
    results.put(Id.of("golemancy", id), new FakeItem(material, 1));
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
      return new FakeItem(type, amount);
    }

    @Override
    public int maxStackSize(Id type) {
      return 64;
    }
  }

  private final class FakeResolver implements ExactItemResolver {
    @Override
    public ExactIngredient ingredient(Id id) {
      return new ExactIngredient(materials.get(id.path().equals("blank_core") ? "FIREWORK_STAR" : "CLAY_BALL"), item -> true);
    }

    @Override
    public ItemRef result(Id id) {
      return results.get(id);
    }
  }
}
