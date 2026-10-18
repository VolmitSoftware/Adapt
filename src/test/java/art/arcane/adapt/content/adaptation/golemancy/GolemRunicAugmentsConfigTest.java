package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ExactIngredient;
import art.arcane.adapt.api.recipe.ExactIngredientBook;
import art.arcane.adapt.api.recipe.ExactItemResolver;
import art.arcane.adapt.api.recipe.RecipeItemCatalog;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GolemRunicAugmentsConfigTest {
  private final GolemRunicAugments.Config config = new GolemRunicAugments.Config();
  private final Map<String, Material> materials = new HashMap<>();
  private final Map<NamespacedKey, ItemStack> results = new HashMap<>();
  private final ConfiguredRecipeParser parser = new ConfiguredRecipeParser(new FakeCatalog(), new FakeResolver(), new ExactIngredientBook());

  @Test
  void theBlankRuneRecipeRingsSmoothStoneWithAmethystAndYieldsTwo() {
    Material shard = material("AMETHYST_SHARD");
    Material stone = material("SMOOTH_STONE");
    ItemStack template = mock(ItemStack.class);
    ItemStack blankRune = mock(ItemStack.class);
    when(template.clone()).thenReturn(blankRune);
    results.put(new NamespacedKey("golemancy", "blank_rune"), template);

    ConfiguredRecipeParser.Result parsed = parser.parse(config.blankRuneRecipe);

    assertThat(parsed.problems()).isEmpty();
    AdaptRecipe.Shaped shaped = parsed.builder().key("test").build();
    assertThat(shaped.getShapes()).containsExactly(" A ", "ASA", " A ");
    assertThat(choice(shaped, 'A')).containsExactly(shard);
    assertThat(choice(shaped, 'S')).containsExactly(stone);
    assertThat(shaped.getResult()).isSameAs(blankRune);
    assertThat(config.blankRuneRecipe.getAmount()).isEqualTo(2);
    assertThat(config.blankRuneRecipe.getTithe()).isZero();
  }

  private Material material(String name) {
    Material material = mock(Material.class);
    when(material.isItem()).thenReturn(true);
    when(material.name()).thenReturn(name);
    materials.put(name, material);
    return material;
  }

  private static List<Material> choice(AdaptRecipe.Shaped shaped, char symbol) {
    RecipeChoice choice = shaped.getIngredients().stream()
        .filter(ingredient -> ingredient.getCharacter() == symbol)
        .findFirst()
        .orElseThrow()
        .getChoice();
    assertThat(choice).isInstanceOf(RecipeChoice.MaterialChoice.class);
    return ((RecipeChoice.MaterialChoice) choice).getChoices();
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
