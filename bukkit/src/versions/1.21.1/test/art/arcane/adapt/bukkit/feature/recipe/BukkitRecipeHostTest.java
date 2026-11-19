package art.arcane.adapt.bukkit.feature.recipe;

import art.arcane.adapt.bukkit.natives.IdentityChoice;
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
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
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
    RecipeSpec.Shaped spec = new RecipeSpec.Shaped(Id.adapt("elevator"), List.of("PPP", "PEP", "PPP"),
        Map.of('P', Ingredient.of(Id.minecraft("oak_planks")), 'E', Ingredient.of(Id.minecraft("ender_pearl"))), resultRef);

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
    RecipeSpec.Shapeless spec = new RecipeSpec.Shapeless(Id.adapt("bad"), List.of(Ingredient.of(Id.of("other", "missing"))), resultRef);

    try (MockedConstruction<ShapelessRecipe> ignored = mockConstruction(ShapelessRecipe.class)) {
      assertThat(translator.translate(spec)).isNull();
    }
  }

  @Test
  void cookingStationsPickTheMatchingRecipeClass() {
    RecipeTranslator translator = translator();
    RecipeSpec.Cooking spec = new RecipeSpec.Cooking(Id.adapt("smelt"), RecipeSpec.CookingStation.FURNACE,
        Ingredient.of(Id.minecraft("cobblestone")), resultRef, 0.5F, 100);

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
    RecipeSpec.Smithing spec = new RecipeSpec.Smithing(Id.adapt("upgrade"), null,
        Ingredient.of(Id.minecraft("diamond_sword")), Ingredient.of(Id.minecraft("netherite_ingot")), resultRef);

    try (MockedConstruction<SmithingRecipe> smithing = mockConstruction(SmithingRecipe.class);
         MockedConstruction<ItemStack> ignoredStacks = mockConstruction(ItemStack.class);
         MockedConstruction<RecipeChoice.ExactChoice> exact = mockConstruction(RecipeChoice.ExactChoice.class)) {
      assertThat(translator.translate(spec)).isSameAs(smithing.constructed().getFirst());
      assertThat(exact.constructed()).hasSize(2);
    }
  }

  @Test
  void matchingIngredientsBecomeIdentityChoicesOfOneExampleThatTestByTheMatcher() {
    ItemRef exampleRef = mock(ItemRef.class);
    ItemStack example = mock(ItemStack.class);
    ItemStack representative = mock(ItemStack.class);
    when(example.isEmpty()).thenReturn(false);
    when(example.clone()).thenReturn(representative);
    ItemStack renamed = stack(Material.FIREWORK_STAR);
    ItemStack lookalike = stack(Material.FIREWORK_STAR);
    ItemStack otherType = stack(Material.STONE);
    ItemRef renamedRef = mock(ItemRef.class);
    ItemRef lookalikeRef = mock(ItemRef.class);
    RecipeTranslator translator = new RecipeTranslator(
        (Id id) -> Material.FIREWORK_STAR,
        (ItemRef item) -> item == exampleRef ? example : null,
        (Id id) -> null);
    Ingredient.Matching matching = new Ingredient.Matching(Id.minecraft("firework_star"), exampleRef,
        (ItemRef item) -> item == renamedRef);
    List<Object> arguments = new ArrayList<>();

    try (MockedStatic<BukkitRefs> refs = mockStatic(BukkitRefs.class);
         MockedConstruction<IdentityChoice> identities = mockConstruction(IdentityChoice.class,
             (IdentityChoice choice, MockedConstruction.Context context) -> arguments.addAll(context.arguments()))) {
      refs.when(() -> BukkitRefs.item(renamed)).thenReturn(renamedRef);
      refs.when(() -> BukkitRefs.item(lookalike)).thenReturn(lookalikeRef);
      refs.when(() -> BukkitRefs.item(otherType)).thenReturn(renamedRef);
      RecipeChoice choice = translator.choice(matching);

      assertThat(choice).isSameAs(identities.constructed().getFirst());
      assertThat(arguments.getFirst()).isSameAs(representative);
      verify(representative).setAmount(1);
      @SuppressWarnings("unchecked")
      Predicate<ItemStack> matches = (Predicate<ItemStack>) arguments.get(1);
      assertThat(matches.test(renamed)).isTrue();
      assertThat(matches.test(lookalike)).isFalse();
      assertThat(matches.test(otherType)).isFalse();
    }
  }

  @Test
  void recipesWithoutIdentityChoicesAreAddedUnchangedAndNotResentWithoutPlayers() {
    Server server = mock(Server.class);
    ShapedRecipe shaped = mock(ShapedRecipe.class);
    when(shaped.getChoiceMap()).thenReturn(Map.of('S', mock(RecipeChoice.ExactChoice.class)));
    ShapelessRecipe shapeless = mock(ShapelessRecipe.class);
    when(shapeless.getChoiceList()).thenReturn(List.of(mock(RecipeChoice.MaterialChoice.class)));
    FurnaceRecipe furnace = mock(FurnaceRecipe.class);
    NamespacedKey key = new NamespacedKey("adapt", "elevator");
    when(server.addRecipe(shaped)).thenReturn(true);
    when(server.addRecipe(shapeless)).thenReturn(true);
    when(server.addRecipe(furnace)).thenReturn(true);
    when(server.removeRecipe(key)).thenReturn(true);

    assertThat(BukkitRecipeVersion.add(server, shaped)).isTrue();
    assertThat(BukkitRecipeVersion.add(server, shapeless)).isTrue();
    assertThat(BukkitRecipeVersion.add(server, furnace)).isTrue();
    BukkitRecipeVersion.remove(server, key);

    verify(server).addRecipe(shaped);
    verify(server).addRecipe(shapeless);
    verify(server).addRecipe(furnace);
    verify(server).removeRecipe(key);
    verify(server, never()).updateRecipes();
  }

  @Test
  void identityChoicesReadBackFromTheServerStayMatchingInTheView() {
    ItemStack representative = mock(ItemStack.class);
    when(representative.clone()).thenReturn(representative);
    ItemStack accepted = mock(ItemStack.class);
    ItemStack refused = mock(ItemStack.class);
    ShapelessRecipe recipe = mock(ShapelessRecipe.class);
    when(recipe.getKey()).thenReturn(new NamespacedKey("adapt", "golem_core"));
    when(recipe.getResult()).thenReturn(result);
    IdentityChoice identity = mock(IdentityChoice.class);
    when(identity.getItemStack()).thenReturn(representative);
    when(identity.test(accepted)).thenReturn(true);
    when(identity.test(refused)).thenReturn(false);
    when(recipe.getChoiceList()).thenReturn(List.of(identity));

    RecipeView view = BukkitRecipeHost.view(recipe);

    ItemRef acceptedRef = mock(ItemRef.class);
    when(acceptedRef.unwrap()).thenReturn(accepted);
    ItemRef refusedRef = mock(ItemRef.class);
    when(refusedRef.unwrap()).thenReturn(refused);
    art.arcane.adapt.platform.recipe.RecipeChoice slot = view.choices().getFirst();
    assertThat(slot.exact().getFirst().unwrap()).isSameAs(representative);
    assertThat(slot.test(acceptedRef)).isTrue();
    assertThat(slot.test(refusedRef)).isFalse();
    assertThat(BukkitRecipeVersion.comparesExactly(identity)).isFalse();
    assertThat(BukkitRecipeVersion.comparesExactly(mock(RecipeChoice.ExactChoice.class))).isTrue();
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
    RecipeSpec spec = new RecipeSpec.Shapeless(Id.adapt("elevator"), List.of(Ingredient.of(Id.minecraft("stone"))), resultRef);
    NamespacedKey key = new NamespacedKey("adapt", "elevator");
    when(translator.translate(spec)).thenReturn(recipe);
    when(server.addRecipe(recipe)).thenReturn(true);
    BukkitRecipeHost host = new BukkitRecipeHost(server, translator);

    assertThat(host.register(spec)).isTrue();
    verify(server, never()).removeRecipe(key);
    assertThat(host.registered()).containsExactly(Id.adapt("elevator"));

    assertThat(host.register(spec)).isTrue();
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
    RecipeSpec spec = new RecipeSpec.Shapeless(Id.adapt("broken"), List.of(Ingredient.of(Id.minecraft("stone"))), resultRef);
    BukkitRecipeHost host = new BukkitRecipeHost(server, translator);

    assertThat(host.register(spec)).isFalse();
    assertThat(host.registered()).isEmpty();
  }

  @Test
  void discoveryUsesTheRecipeKey() {
    Server server = mock(Server.class);
    BukkitRecipeHost host = new BukkitRecipeHost(server, mock(RecipeTranslator.class));
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

  private static ItemStack stack(Material type) {
    ItemStack stack = mock(ItemStack.class);
    when(stack.getType()).thenReturn(type);
    when(stack.getAmount()).thenReturn(1);
    return stack;
  }

  private RecipeTranslator translator() {
    return new RecipeTranslator(
        (Id id) -> id.namespace().equals("minecraft") ? Material.STONE : null,
        (ItemRef item) -> item == resultRef ? result : null,
        (Id id) -> null);
  }
}
