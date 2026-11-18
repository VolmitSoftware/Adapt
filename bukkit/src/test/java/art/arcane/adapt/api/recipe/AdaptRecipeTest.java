package art.arcane.adapt.api.recipe;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdaptRecipeTest {
  @Test
  void everyRecipeTypeExposesItsResultAndDefaultsToLevelOne() {
    ItemStack result = mock(ItemStack.class);
    List<AdaptRecipe> recipes = List.of(
        AdaptRecipe.smoker().result(result).build(),
        AdaptRecipe.furnace().result(result).build(),
        AdaptRecipe.campfire().result(result).build(),
        AdaptRecipe.blast().result(result).build(),
        AdaptRecipe.shapeless().result(result).build(),
        AdaptRecipe.stonecutter().result(result).build(),
        AdaptRecipe.shaped().result(result).build(),
        AdaptRecipe.smithing().result(result).build()
    );

    for (AdaptRecipe recipe : recipes) {
      assertThat(recipe.getResult()).isSameAs(result);
      assertThat(recipe.getRequiredLevel()).isEqualTo(1);
    }
  }

  @Test
  void recipeBuildersRetainExplicitUnlockLevels() {
    AdaptRecipe recipe = AdaptRecipe.shaped()
        .result(mock(ItemStack.class))
        .requiredLevel(4)
        .build();

    assertThat(recipe.getRequiredLevel()).isEqualTo(4);
  }

  @Test
  void shapedRecipesBecomeSpecsUnderTheAdaptNamespaceWithTheirLevel() {
    ItemStack result = mock(ItemStack.class);
    AdaptRecipe recipe = AdaptRecipe.shaped()
        .key("blocking-phalanx-netherite")
        .shape("NNN")
        .shape(" S ")
        .ingredient(new MaterialChar('N', Material.NETHERITE_INGOT))
        .ingredient(new MaterialChar('S', Material.SHIELD))
        .requiredLevel(2)
        .result(result)
        .build();

    RecipeSpec.Shaped spec = (RecipeSpec.Shaped) recipe.toSpec();

    assertThat(spec.key()).isEqualTo(Id.adapt("blocking-phalanx-netherite"));
    assertThat(recipe.getNSKey()).isEqualTo(new NamespacedKey("adapt", "blocking-phalanx-netherite"));
    assertThat(spec.shape()).containsExactly("NNN", " S ");
    assertThat(spec.ingredients()).containsExactlyInAnyOrderEntriesOf(Map.of(
        'N', Ingredient.of(Id.minecraft("netherite_ingot")),
        'S', Ingredient.of(Id.minecraft("shield"))));
    assertThat(spec.requiredLevel()).isEqualTo(2);
    assertThat(BukkitRefs.item(spec.result())).isSameAs(result);
  }

  @Test
  void materialCharsListEveryAcceptedMaterial() {
    MaterialChar wools = new MaterialChar('W', Material.WHITE_WOOL, Material.BLACK_WOOL);

    assertThat(wools.getIngredient()).isEqualTo(new Ingredient.AnyOf(Set.of(Id.minecraft("white_wool"), Id.minecraft("black_wool"))));
  }

  @Test
  void shapelessCookingStonecutterAndSmithingRecipesKeepTheirInputs() {
    ItemStack result = mock(ItemStack.class);

    RecipeSpec.Shapeless shapeless = (RecipeSpec.Shapeless) AdaptRecipe.shapeless().key("axe-swapbirchoak")
        .ingredient(Material.BIRCH_LOG).ingredient(Material.OAK_SAPLING).result(result).build().toSpec();
    RecipeSpec.Cooking campfire = (RecipeSpec.Cooking) AdaptRecipe.campfire().key("herbalism-roast")
        .ingredient(Material.ROTTEN_FLESH).experience(0.5F).cookTime(600).result(result).build().toSpec();
    RecipeSpec.Cooking smoker = (RecipeSpec.Cooking) AdaptRecipe.smoker().key("s").ingredient(Material.BEEF).result(result).build().toSpec();
    RecipeSpec.Cooking blast = (RecipeSpec.Cooking) AdaptRecipe.blast().key("b").ingredient(Material.RAW_IRON).result(result).build().toSpec();
    RecipeSpec.Cooking furnace = (RecipeSpec.Cooking) AdaptRecipe.furnace().key("f").ingredient(Material.SAND).result(result).build().toSpec();
    RecipeSpec.Stonecutting cutter = (RecipeSpec.Stonecutting) AdaptRecipe.stonecutter().key("c").ingredient(Material.STONE)
        .result(result).build().toSpec();
    RecipeSpec.Smithing smithing = (RecipeSpec.Smithing) AdaptRecipe.smithing().key("u").base(Material.DIAMOND_SWORD)
        .addition(Material.NETHERITE_INGOT).requiredLevel(3).result(result).build().toSpec();

    assertThat(shapeless.ingredients()).containsExactly(Ingredient.of(Id.minecraft("birch_log")), Ingredient.of(Id.minecraft("oak_sapling")));
    assertThat(campfire.station()).isEqualTo(RecipeSpec.CookingStation.CAMPFIRE);
    assertThat(campfire.input()).isEqualTo(Ingredient.of(Id.minecraft("rotten_flesh")));
    assertThat(campfire.experience()).isEqualTo(0.5F);
    assertThat(campfire.cookTicks()).isEqualTo(600);
    assertThat(smoker.station()).isEqualTo(RecipeSpec.CookingStation.SMOKER);
    assertThat(blast.station()).isEqualTo(RecipeSpec.CookingStation.BLAST_FURNACE);
    assertThat(furnace.station()).isEqualTo(RecipeSpec.CookingStation.FURNACE);
    assertThat(cutter.input()).isEqualTo(Ingredient.of(Id.minecraft("stone")));
    assertThat(smithing.template()).isNull();
    assertThat(smithing.base()).isEqualTo(Ingredient.of(Id.minecraft("diamond_sword")));
    assertThat(smithing.addition()).isEqualTo(Ingredient.of(Id.minecraft("netherite_ingot")));
    assertThat(smithing.requiredLevel()).isEqualTo(3);
  }

  @Test
  void recipesAreMatchedByKey() {
    AdaptRecipe recipe = AdaptRecipe.shaped().key("crafting-backpack").result(mock(ItemStack.class)).build();
    ShapedRecipe same = mock(ShapedRecipe.class);
    ShapedRecipe other = mock(ShapedRecipe.class);
    when(same.getKey()).thenReturn(new NamespacedKey("adapt", "crafting-backpack"));
    when(other.getKey()).thenReturn(new NamespacedKey("adapt", "crafting-backpack-cycle"));

    assertThat(recipe.is(same)).isTrue();
    assertThat(recipe.is(other)).isFalse();
    assertThat(recipe.is(mock(Recipe.class))).isFalse();
    assertThat(recipe.is(null)).isFalse();
  }
}
