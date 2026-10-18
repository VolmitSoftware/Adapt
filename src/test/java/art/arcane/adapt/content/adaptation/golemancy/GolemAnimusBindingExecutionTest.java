package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemSettings;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ExactIngredient;
import art.arcane.adapt.api.recipe.ExactIngredientBook;
import art.arcane.adapt.api.recipe.ExactItemResolver;
import art.arcane.adapt.api.recipe.RecipeItemCatalog;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Snowman;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemAnimusBindingExecutionTest extends AdaptTestBase {
  private static final NamespacedKey CROOK_KEY = new NamespacedKey("adapt", "golemancy-crook");
  private static final NamespacedKey BLANK_CORE_KEY = new NamespacedKey("adapt", "golemancy-blank-core");

  private final Map<String, Material> materials = new HashMap<>();
  private final Map<NamespacedKey, ItemStack> results = new HashMap<>();
  private final ExactIngredientBook book = new ExactIngredientBook();
  private MockedStatic<ConfiguredRecipeParser> parsers;
  private MockedStatic<GolemancyRuntime> runtime;
  private GolemAnimusBinding adaptation;

  @BeforeEach
  void construct() {
    when(plugin.namespace()).thenReturn("adapt");
    for (String name : List.of("IRON_HOE", "AMETHYST_SHARD", "REDSTONE", "COPPER_INGOT", "ENDER_PEARL")) {
      material(name);
    }
    result("crook");
    result("blank_core");
    ConfiguredRecipeParser parser = new ConfiguredRecipeParser(new FakeCatalog(), new FakeResolver(), book);
    parsers = mockStatic(ConfiguredRecipeParser.class, CALLS_REAL_METHODS);
    parsers.when(() -> ConfiguredRecipeParser.bukkit(GolemItems.resolver(), GolemItems.recipeBook())).thenReturn(parser);
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::settings).thenReturn(GolemSettings.defaults());
    adaptation = new GolemAnimusBinding();
  }

  @AfterEach
  void close() {
    runtime.close();
    parsers.close();
  }

  @Test
  void bothRecipesRegisterAtLevelOneUnderGolemancyKeysWithTheirTithes() {
    List<AdaptRecipe> recipes = adaptation.getRecipes();

    assertThat(recipes).extracting(AdaptRecipe::getNSKey).containsExactly(CROOK_KEY, BLANK_CORE_KEY);
    assertThat(recipes).allMatch(recipe -> recipe.getRequiredLevel() == 1);
    assertThat(book.satisfiedBy(CROOK_KEY, List.of())).isTrue();
    assertThat(book.satisfiedBy(BLANK_CORE_KEY, List.of())).isTrue();
    assertThat(GolemItems.recipeTithe().levels(keyed(CROOK_KEY))).isZero();
    assertThat(GolemItems.recipeTithe().levels(keyed(BLANK_CORE_KEY))).isEqualTo(1);
  }

  @Test
  void configurationLoadsAndEveryLevelHasAPositiveCostAndProgress() {
    assertThat(adaptation.reloadConfigFromDisk(false)).isTrue();
    assertThat(adaptation.getConfig()).isNotNull();
    assertThat(adaptation.getMaxLevel()).isEqualTo(5);
    assertThat(adaptation.getLevelPercent(0)).isZero();
    assertThat(adaptation.getLevelPercent(Integer.MAX_VALUE)).isEqualTo(1D);
    double previous = 0D;
    for (int level = 1; level <= adaptation.getMaxLevel(); level++) {
      assertThat(adaptation.getCostFor(level)).as("cost at level %s", level).isPositive();
      assertThat(adaptation.getLevelPercent(level)).isBetween(previous, 1D);
      previous = adaptation.getLevelPercent(level);
    }
    assertThat(adaptation.isRuntimeRegistered()).isFalse();
  }

  @Test
  void everyLevelShowsItsCapRitualAndTithes() {
    assertThat(lore(1)).containsExactly("+ 1 Golems You Can Own", "* 4 Seconds per Binding Ritual",
        "- 1 Levels to Bind a Frost Golem", "- 2 Levels to Bind a Copper Golem", "- 3 Levels to Bind an Iron Golem");
    assertThat(lore(2)).containsExactly("+ 2 Golems You Can Own", "* 3.5 Seconds per Binding Ritual",
        "- 1 Levels to Bind a Frost Golem", "- 2 Levels to Bind a Copper Golem", "- 3 Levels to Bind an Iron Golem");
    assertThat(lore(3)).containsExactly("+ 3 Golems You Can Own", "* 3 Seconds per Binding Ritual",
        "- 1 Levels to Bind a Frost Golem", "- 1 Levels to Bind a Copper Golem", "- 2 Levels to Bind an Iron Golem");
    assertThat(lore(4)).containsExactly("+ 5 Golems You Can Own", "* 2.5 Seconds per Binding Ritual",
        "- 1 Levels to Bind a Frost Golem", "- 1 Levels to Bind a Copper Golem", "- 2 Levels to Bind an Iron Golem");
    assertThat(lore(5)).containsExactly("+ 7 Golems You Can Own", "* 2 Seconds per Binding Ritual",
        "- 0 Levels to Bind a Frost Golem", "- 1 Levels to Bind a Copper Golem", "- 1 Levels to Bind an Iron Golem");
  }

  @Test
  void aDisabledReloadTurnsTheAdaptationOff() {
    assertThat(adaptation.reloadConfigSnapshot("enabled = false\nmaxLevel = 1\n", dataFolder.toPath().resolve("disabled.toml").toFile(), false)).isTrue();
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
      assertThat(adaptation.isEnabled()).isFalse();
    }
    assertThat(adaptation.getMaxLevel()).isEqualTo(1);
    assertThat(adaptation.getLevelPercent(2)).isEqualTo(1D);
  }

  @Test
  void ritualSecondsAreClampedWhenTheConfigLoads() {
    assertThat(adaptation.reloadConfigSnapshot("ritualSecondsByLevel = [0.1, 99.0]\n", dataFolder.toPath().resolve("clamp.toml").toFile(), false)).isTrue();

    assertThat(adaptation.getConfig().ritualSecondsByLevel).containsExactly(0.5D, 30D);
    assertThat(GolemAnimusBinding.ritualTicks(adaptation.getConfig(), 1)).isEqualTo(10);
    assertThat(GolemAnimusBinding.ritualTicks(adaptation.getConfig(), 2)).isEqualTo(600);
  }

  @Test
  void sneakClickingAWildSkeletonWithACoreIsLeftAlone() {
    PlayerInteractEntityEvent skeleton = coreClick(mock(Skeleton.class));
    PlayerInteractEntityEvent snowGolem = coreClick(mock(Snowman.class));

    verify(skeleton, never()).setCancelled(anyBoolean());
    verify(snowGolem).setCancelled(true);
  }

  private PlayerInteractEntityEvent coreClick(Mob target) {
    Player player = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    ItemStack core = mock(ItemStack.class);
    when(player.isSneaking()).thenReturn(true);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    when(target.getUniqueId()).thenReturn(UUID.randomUUID());
    when(player.getInventory()).thenReturn(inventory);
    when(inventory.getItemInMainHand()).thenReturn(core);
    PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
    when(event.getHand()).thenReturn(EquipmentSlot.HAND);
    when(event.getPlayer()).thenReturn(player);
    when(event.getRightClicked()).thenReturn(target);
    GolemAnimusBinding learned = spy(adaptation);
    doReturn(3).when(learned).getActiveLevel(player);
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.empty());
    try (MockedStatic<J> scheduling = mockStatic(J.class);
         MockedStatic<GolemItems> items = mockStatic(GolemItems.class, CALLS_REAL_METHODS)) {
      scheduling.when(() -> J.isOwnedByCurrentRegion(any(Entity.class))).thenReturn(true);
      items.when(() -> GolemItems.coreOf(core)).thenReturn(Optional.of(GolemCoreType.BLANK));
      learned.on(event);
    }
    return event;
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
      return null;
    }

    @Override
    public ItemStack result(NamespacedKey id) {
      return results.get(id);
    }
  }
}
