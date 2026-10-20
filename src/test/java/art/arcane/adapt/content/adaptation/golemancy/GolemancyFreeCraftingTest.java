package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.ListenerDispatch;
import art.arcane.adapt.api.golem.GolemBrainSettings;
import art.arcane.adapt.api.golem.GolemSettings;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ExactIngredient;
import art.arcane.adapt.api.recipe.ExactIngredientBook;
import art.arcane.adapt.api.recipe.ExactItemResolver;
import art.arcane.adapt.api.recipe.RecipeItemCatalog;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GolemancyFreeCraftingTest extends AdaptTestBase {
  private static final NamespacedKey BLANK_CORE_KEY = new NamespacedKey("adapt", "golemancy-blank-core");
  private static final NamespacedKey GUARDIAN_CORE_KEY = new NamespacedKey("adapt", "golemancy-guardian-core");

  private final Map<String, Material> materials = new HashMap<>();
  private final Map<NamespacedKey, ItemStack> results = new HashMap<>();
  private final ExactIngredientBook book = new ExactIngredientBook();
  private final AtomicBoolean resultShown = new AtomicBoolean();
  private MockedStatic<ConfiguredRecipeParser> parsers;
  private MockedStatic<GolemancyRuntime> runtime;
  private Player player;
  private CraftingInventory inventory;

  @BeforeEach
  void construct() {
    when(plugin.namespace()).thenReturn("adapt");
    for (String name : List.of("IRON_HOE", "AMETHYST_SHARD", "REDSTONE", "COPPER_INGOT", "ENDER_PEARL", "IRON_SWORD", "IRON_INGOT",
        "SHIELD", "FIREWORK_STAR", "CLAY_BALL")) {
      material(name);
    }
    for (String id : List.of("crook", "blank_core", "blank_node", "core/guardian", "node/guard")) {
      result(id);
    }
    ConfiguredRecipeParser parser = new ConfiguredRecipeParser(new FakeCatalog(), new FakeResolver(), book);
    parsers = mockStatic(ConfiguredRecipeParser.class, CALLS_REAL_METHODS);
    parsers.when(() -> ConfiguredRecipeParser.bukkit(GolemItems.resolver(), GolemItems.recipeBook())).thenReturn(parser);
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::settings).thenReturn(GolemSettings.defaults());
    runtime.when(GolemancyRuntime::brainSettings).thenReturn(GolemBrainSettings.defaults());

    player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000048"));
    when(player.getLevel()).thenReturn(0);
    when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
    when(player.getTicksLived()).thenReturn(100);
    inventory = mock(CraftingInventory.class);
    when(inventory.getMatrix()).thenReturn(new ItemStack[9]);
    doAnswer(invocation -> {
      resultShown.set(invocation.getArgument(0) != null);
      return null;
    }).when(inventory).setResult(any());
  }

  @AfterEach
  void close() {
    runtime.close();
    parsers.close();
  }

  @Test
  void aPlayerWithNoLevelsSeesTheBlankAnimusCoreResult() {
    GolemAnimusBinding binding = new GolemAnimusBinding();

    assertThat(binding.getRecipes()).extracting(AdaptRecipe::getNSKey).contains(BLANK_CORE_KEY);
    assertThat(prepare(BLANK_CORE_KEY)).isTrue();
  }

  @Test
  void aPlayerWithNoLevelsSeesARoleCoreResult() {
    GolemGuardianCore guardian = new GolemGuardianCore();

    assertThat(guardian.getRecipes()).extracting(AdaptRecipe::getNSKey).contains(GUARDIAN_CORE_KEY);
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

    CrafterCraftEvent blank = new CrafterCraftEvent(mock(Block.class), recipe(BLANK_CORE_KEY), mock(ItemStack.class));
    CrafterCraftEvent guardian = new CrafterCraftEvent(mock(Block.class), recipe(GUARDIAN_CORE_KEY), mock(ItemStack.class));
    ListenerDispatch.dispatch(plugin, blank, GolemItems.craftCounter());
    ListenerDispatch.dispatch(plugin, guardian, GolemItems.craftCounter());

    assertThat(blank.isCancelled()).isFalse();
    assertThat(guardian.isCancelled()).isFalse();
  }

  private boolean prepare(NamespacedKey key) {
    resultShown.set(true);
    PrepareItemCraftEvent event = mock(PrepareItemCraftEvent.class);
    InventoryView view = mock(InventoryView.class);
    ShapedRecipe recipe = recipe(key);
    when(view.getPlayer()).thenReturn(player);
    when(event.getView()).thenReturn(view);
    when(event.getInventory()).thenReturn(inventory);
    when(event.getRecipe()).thenReturn(recipe);
    ListenerDispatch.dispatch(plugin, event, GolemItems.craftCounter());
    return resultShown.get();
  }

  private CraftItemEvent click(NamespacedKey key) {
    CraftItemEvent event = mock(CraftItemEvent.class);
    AtomicBoolean cancelled = new AtomicBoolean();
    ShapedRecipe recipe = recipe(key);
    when(event.getWhoClicked()).thenReturn(player);
    when(event.getRecipe()).thenReturn(recipe);
    when(event.getInventory()).thenReturn(inventory);
    when(event.isCancelled()).thenAnswer(invocation -> cancelled.get());
    doAnswer(invocation -> {
      cancelled.set(invocation.getArgument(0));
      return null;
    }).when(event).setCancelled(anyBoolean());
    ListenerDispatch.dispatch(plugin, event, GolemItems.craftCounter());
    return event;
  }

  private static ShapedRecipe recipe(NamespacedKey key) {
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
    Material material = materials.get(id.contains("node") ? "CLAY_BALL" : "FIREWORK_STAR");
    when(template.getType()).thenReturn(material);
    when(copy.getType()).thenReturn(material);
    when(template.clone()).thenReturn(copy);
    results.put(new NamespacedKey("golemancy", id), template);
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
      return new ExactIngredient(materials.get(id.getKey().equals("blank_core") ? "FIREWORK_STAR" : "CLAY_BALL"), item -> true);
    }

    @Override
    public ItemStack result(NamespacedKey id) {
      return results.get(id);
    }
  }
}
