package art.arcane.adapt.bukkit.feature.recipe;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.recipe.RecipeView;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingRecipe;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BukkitRecipeHostTest {
  private final ItemRef resultRef = mock(ItemRef.class);
  private final ItemStack result = mock(ItemStack.class);

  @Test
  void shapedRecipesKeepShapeAndResolvedChoices() {
    RecipeTranslator translator = translator();
    RecipeSpec.Shaped spec = RecipeSpec.shaped(Id.adapt("elevator"), List.of("PPP", "PEP", "PPP"), Map.of('P', Ingredient.of(Id.minecraft("oak_planks")), 'E', Ingredient.of(Id.minecraft("ender_pearl"))), resultRef);

    try (MockedConstruction<ShapedRecipe> shaped = mockConstruction(ShapedRecipe.class, (ShapedRecipe recipe, MockedConstruction.Context context) -> {
      assertThat(context.arguments().get(0)).isEqualTo(new NamespacedKey("adapt", "elevator"));
      assertThat(context.arguments().get(1)).isSameAs(result);
    });
         MockedConstruction<RecipeChoice.MaterialChoice> choices = mockConstruction(RecipeChoice.MaterialChoice.class)) {
      Recipe recipe = translator.translate(spec);

      ShapedRecipe built = shaped.constructed().getFirst();
      assertThat(recipe).isSameAs(built);
      verify(built).shape("PPP", "PEP", "PPP");
      assertThat(choices.constructed()).hasSize(2);
    }
  }

  @Test
  void shapedViewsDropTheNativeEmptyPlaceholderLikeAMissingCell() {
    ShapedRecipe recipe = mock(ShapedRecipe.class);
    when(recipe.getKey()).thenReturn(NamespacedKey.minecraft("iron_chestplate"));
    when(recipe.getResult()).thenReturn(result);
    when(recipe.getShape()).thenReturn(new String[]{"abc", "def", "ghi"});
    RecipeChoice.MaterialChoice iron = mock(RecipeChoice.MaterialChoice.class);
    when(iron.getChoices()).thenReturn(List.of(Material.IRON_INGOT));
    Map<Character, RecipeChoice> choices = new HashMap<>();
    for (char symbol : "acdefghi".toCharArray()) {
      choices.put(symbol, iron);
    }
    choices.put('b', BukkitRecipeVersion.emptyChoice());
    when(recipe.getChoiceMap()).thenReturn(choices);

    RecipeView view = BukkitRecipeHost.view(recipe);

    assertThat(view.shapedChoices()).hasSize(8).doesNotContainKey('b');
  }

  @Test
  void unresolvedIngredientsRejectTheRecipe() {
    RecipeTranslator translator = translator();
    RecipeSpec.Shapeless spec = RecipeSpec.shapeless(Id.adapt("bad"), List.of(Ingredient.of(Id.of("other", "missing"))), resultRef);

    try (MockedConstruction<ShapelessRecipe> ignored = mockConstruction(ShapelessRecipe.class)) {
      assertThat(translator.translate(spec)).isNull();
    }
  }

  @Test
  void cookingStationsPickTheMatchingRecipeClass() {
    RecipeTranslator translator = translator();
    RecipeSpec.Cooking spec = RecipeSpec.cooking(Id.adapt("smelt"), RecipeSpec.CookingStation.FURNACE, Ingredient.of(Id.minecraft("cobblestone")), resultRef, 0.5F, 100);

    try (MockedConstruction<FurnaceRecipe> furnace = mockConstruction(FurnaceRecipe.class, (FurnaceRecipe recipe, MockedConstruction.Context context) -> {
      assertThat(context.arguments().get(3)).isEqualTo(0.5F);
      assertThat(context.arguments().get(4)).isEqualTo(100);
    });
         MockedConstruction<RecipeChoice.MaterialChoice> ignored = mockConstruction(RecipeChoice.MaterialChoice.class)) {
      assertThat(translator.translate(spec)).isSameAs(furnace.constructed().getFirst());
    }
  }

  @Test
  void smithingWithoutTemplateUsesExactChoicesLikeThePlugin() {
    RecipeTranslator translator = translator();
    RecipeSpec.Smithing spec = RecipeSpec.smithing(Id.adapt("upgrade"), null, Ingredient.of(Id.minecraft("diamond_sword")), Ingredient.of(Id.minecraft("netherite_ingot")), resultRef);

    try (MockedConstruction<SmithingRecipe> smithing = mockConstruction(SmithingRecipe.class);
         MockedConstruction<ItemStack> ignoredStacks = mockConstruction(ItemStack.class);
         MockedConstruction<RecipeChoice.ExactChoice> exact = mockConstruction(RecipeChoice.ExactChoice.class)) {
      assertThat(translator.translate(spec)).isSameAs(smithing.constructed().getFirst());
      assertThat(exact.constructed()).hasSize(2);
    }
  }

  @Test
  void matchingIngredientsFallBackToAnExactChoiceOfOneExample() {
    ItemRef exampleRef = mock(ItemRef.class);
    ItemStack example = mock(ItemStack.class);
    ItemStack representative = mock(ItemStack.class);
    when(example.isEmpty()).thenReturn(false);
    when(example.clone()).thenReturn(representative);
    RecipeTranslator translator = new RecipeTranslator(
        (Id id) -> Material.FIREWORK_STAR,
        (ItemRef item) -> item == exampleRef ? example : null,
        (Id id) -> null);
    Ingredient.Matching matching = new Ingredient.Matching(Id.minecraft("firework_star"), exampleRef, (ItemRef item) -> true);

    try (MockedConstruction<RecipeChoice.ExactChoice> exact = mockConstruction(RecipeChoice.ExactChoice.class,
        (RecipeChoice.ExactChoice choice, MockedConstruction.Context context) -> assertThat(context.arguments().getFirst()).isSameAs(representative))) {
      RecipeChoice choice = translator.choice(matching);

      assertThat(choice).isSameAs(exact.constructed().getFirst());
      verify(representative).setAmount(1);
    }
  }

  @Test
  void matchingIngredientsWithoutAnExampleRejectTheRecipe() {
    RecipeTranslator translator = new RecipeTranslator((Id id) -> Material.FIREWORK_STAR, (ItemRef item) -> null, (Id id) -> null);

    assertThat(translator.choice(new Ingredient.Matching(Id.minecraft("firework_star"), mock(ItemRef.class), (ItemRef item) -> true))).isNull();
  }

  @Test
  void registeringReplacesAndTracksKeys() {
    Server server = mock(Server.class);
    RecipeTranslator translator = mock(RecipeTranslator.class);
    Recipe recipe = mock(Recipe.class);
    RecipeSpec spec = RecipeSpec.shapeless(Id.adapt("elevator"), List.of(Ingredient.of(Id.minecraft("stone"))), resultRef);
    NamespacedKey key = new NamespacedKey("adapt", "elevator");
    when(translator.translate(spec)).thenReturn(recipe);
    when(server.addRecipe(recipe)).thenReturn(true);
    BukkitRecipeHost host = new BukkitRecipeHost(server, translator, registry());

    assertThat(host.add(spec)).isTrue();
    verify(server, never()).removeRecipe(key);
    assertThat(host.registered()).containsExactly(Id.adapt("elevator"));

    assertThat(host.add(spec)).isTrue();
    InOrder order = inOrder(server);
    order.verify(server).removeRecipe(key);
    order.verify(server).addRecipe(recipe);

    host.unregister(Id.adapt("elevator"));
    assertThat(host.registered()).isEmpty();
  }

  @Test
  void rejectedRecipesAreNotTracked() {
    Server server = mock(Server.class);
    RecipeTranslator translator = mock(RecipeTranslator.class);
    RecipeSpec spec = RecipeSpec.shapeless(Id.adapt("broken"), List.of(Ingredient.of(Id.minecraft("stone"))), resultRef);
    BukkitRecipeHost host = new BukkitRecipeHost(server, translator, registry());

    assertThat(host.add(spec)).isFalse();
    assertThat(host.registered()).isEmpty();
  }

  @Test
  void discoveryUsesTheRecipeKey() {
    Server server = mock(Server.class);
    BukkitRecipeHost host = new BukkitRecipeHost(server, mock(RecipeTranslator.class), registry());
    PlayerRef player = mock(PlayerRef.class);
    Player bukkit = mock(Player.class);
    when(bukkit.isOnline()).thenReturn(true);

    try (MockedStatic<BukkitRefs> refs = mockStatic(BukkitRefs.class)) {
      refs.when(() -> BukkitRefs.player(player)).thenReturn(bukkit);
      host.discover(player, Id.adapt("elevator"));
      host.undiscover(player, Id.adapt("other"));
    }

    verify(bukkit).discoverRecipe(new NamespacedKey("adapt", "elevator"));
    verify(bukkit).undiscoverRecipe(new NamespacedKey("adapt", "other"));
  }

  @Test
  void predicateIngredientsStayInTheViewAndMatchThroughTheServerChoice() {
    ShapelessRecipe recipe = mock(ShapelessRecipe.class);
    when(recipe.getKey()).thenReturn(new NamespacedKey("adapt", "golem_core"));
    when(recipe.getResult()).thenReturn(result);
    ItemStack example = mock(ItemStack.class);
    when(example.clone()).thenReturn(example);
    ItemStack accepted = mock(ItemStack.class);
    ItemStack refused = mock(ItemStack.class);
    RecipeChoice custom = mock(RecipeChoice.class);
    when(custom.getItemStack()).thenReturn(example);
    when(custom.test(accepted)).thenReturn(true);
    when(custom.test(refused)).thenReturn(false);
    when(recipe.getChoiceList()).thenReturn(Arrays.asList(custom, BukkitRecipeVersion.emptyChoice()));

    RecipeView view = BukkitRecipeHost.view(recipe);

    ItemRef acceptedRef = mock(ItemRef.class);
    when(acceptedRef.unwrap()).thenReturn(accepted);
    ItemRef refusedRef = mock(ItemRef.class);
    when(refusedRef.unwrap()).thenReturn(refused);
    art.arcane.adapt.platform.recipe.RecipeChoice slot = view.choices().getFirst();
    assertThat(view.choices()).hasSize(2);
    assertThat(slot.isExact()).isTrue();
    assertThat(slot.exact().getFirst().unwrap()).isSameAs(example);
    assertThat(slot.test(acceptedRef)).isTrue();
    assertThat(slot.test(refusedRef)).isFalse();
  }

  @Test
  void recipesForReadsTheIndexAndRebuildsOnlyAfterInvalidation() {
    List<Recipe> all = new ArrayList<>(List.of(recipe(Material.IRON_SWORD, (short) 0), recipe(Material.IRON_SWORD, (short) 5),
        recipe(Material.STONE, (short) 0), recipe(Material.IRON_SWORD, (short) 0)));
    AtomicInteger scans = new AtomicInteger();
    BukkitRecipeRegistry registry = new BukkitRecipeRegistry(() -> {
      scans.incrementAndGet();
      return List.copyOf(all).iterator();
    }, Event.class, () -> 0L);
    BukkitRecipeHost host = new BukkitRecipeHost(mock(Server.class), mock(RecipeTranslator.class), registry);
    ItemRef sword = stack(Material.IRON_SWORD, (short) 0);
    ItemRef anySword = stack(Material.IRON_SWORD, (short) -1);
    ItemRef dirt = stack(Material.DIRT, (short) 0);

    try (MockedStatic<BukkitRefs> refs = mockStatic(BukkitRefs.class)) {
      refs.when(() -> BukkitRefs.item(any(ItemRef.class))).thenAnswer(invocation -> invocation.<ItemRef>getArgument(0).unwrap());
      refs.when(() -> BukkitRefs.item(any(ItemStack.class))).thenReturn(resultRef);

      assertThat(host.recipesFor(sword)).hasSize(scan(all, Material.IRON_SWORD, (short) 0)).hasSize(2);
      assertThat(host.recipesFor(anySword)).hasSize(scan(all, Material.IRON_SWORD, (short) -1)).hasSize(3);
      assertThat(host.recipesFor(dirt)).isEmpty();
      assertThat(scans).hasValue(1);

      all.add(recipe(Material.IRON_SWORD, (short) 0));
      assertThat(host.recipesFor(sword)).hasSize(2);
      registry.invalidate();
      assertThat(host.recipesFor(sword)).hasSize(scan(all, Material.IRON_SWORD, (short) 0)).hasSize(3);
      assertThat(scans).hasValue(2);
    }
  }

  @Test
  void registeringInvalidatesTheIndex() {
    Server server = mock(Server.class);
    RecipeTranslator translator = mock(RecipeTranslator.class);
    RecipeSpec spec = RecipeSpec.shapeless(Id.adapt("elevator"), List.of(Ingredient.of(Id.minecraft("stone"))), resultRef);
    Recipe recipe = mock(Recipe.class);
    when(translator.translate(spec)).thenReturn(recipe);
    when(server.addRecipe(recipe)).thenReturn(true);
    BukkitRecipeRegistry registry = registry();
    BukkitRecipeHost host = new BukkitRecipeHost(server, translator, registry);
    Object before = registry.generation();

    host.add(spec);
    Object registered = registry.generation();
    host.unregister(Id.adapt("elevator"));

    assertThat(registered).isNotSameAs(before);
    assertThat(registry.generation()).isNotSameAs(registered);
  }

  private static int scan(List<Recipe> recipes, Material type, short damage) {
    int matches = 0;
    for (Recipe recipe : recipes) {
      ItemStack result = recipe.getResult();
      if (result.getType() == type && (damage == -1 || result.getDurability() == damage)) {
        matches++;
      }
    }
    return matches;
  }

  private static Recipe recipe(Material type, short damage) {
    Recipe recipe = mock(Recipe.class);
    ItemStack result = mock(ItemStack.class);
    when(result.getType()).thenReturn(type);
    when(result.getDurability()).thenReturn(damage);
    when(recipe.getResult()).thenReturn(result);
    return recipe;
  }

  private static ItemRef stack(Material type, short damage) {
    ItemStack handle = mock(ItemStack.class);
    when(handle.getType()).thenReturn(type);
    when(handle.getDurability()).thenReturn(damage);
    ItemRef ref = mock(ItemRef.class);
    when(ref.isEmpty()).thenReturn(false);
    when(ref.unwrap()).thenReturn(handle);
    return ref;
  }

  private static BukkitRecipeRegistry registry() {
    return new BukkitRecipeRegistry(() -> List.<Recipe>of().iterator(), Event.class, () -> 0L);
  }

  private RecipeTranslator translator() {
    return new RecipeTranslator(
        (Id id) -> id.namespace().equals("minecraft") ? Material.STONE : null,
        (ItemRef item) -> item == resultRef ? result : null,
        (Id id) -> null);
  }
}
