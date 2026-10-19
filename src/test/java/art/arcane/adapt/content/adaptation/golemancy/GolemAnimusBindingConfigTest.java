package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ConfiguredShapedRecipe;
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
import java.util.function.IntUnaryOperator;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GolemAnimusBindingConfigTest {
  private final GolemAnimusBinding.Config config = new GolemAnimusBinding.Config();
  private final Map<String, Material> materials = new HashMap<>();
  private final Map<NamespacedKey, ItemStack> results = new HashMap<>();
  private final ExactIngredientBook book = new ExactIngredientBook();
  private final ConfiguredRecipeParser parser = new ConfiguredRecipeParser(new FakeCatalog(), new FakeResolver(), book);

  @Test
  void golemCapsFollowTheLevelTableUnderTheHardCap() {
    assertThat(perLevel(level -> GolemAnimusBinding.golemCap(config, level, 32))).containsExactly(1, 2, 3, 5, 7);
    assertThat(perLevel(level -> GolemAnimusBinding.golemCap(config, level, 4))).containsExactly(1, 2, 3, 4, 4);
  }

  @Test
  void ritualsShortenFromFourSecondsToTwo() {
    assertThat(perLevel(level -> GolemAnimusBinding.ritualTicks(config, level))).containsExactly(80, 70, 60, 50, 40);
  }

  @Test
  void bindTithesFollowTheLevelTablePerBody() {
    assertThat(perLevel(level -> GolemAnimusBinding.tithe(config, GolemBody.FROST, level))).containsExactly(1, 1, 1, 1, 0);
    assertThat(perLevel(level -> GolemAnimusBinding.tithe(config, GolemBody.COPPER, level))).containsExactly(2, 2, 1, 1, 1);
    assertThat(perLevel(level -> GolemAnimusBinding.tithe(config, GolemBody.IRON, level))).containsExactly(3, 3, 2, 2, 1);
  }

  @Test
  void builderExclusivityAndVillageGolemsDefaultToThePlan() {
    assertThat(config.builderExclusiveSeconds).isEqualTo(300);
    assertThat(config.allowBindingVillageGolems).isFalse();
  }

  @Test
  void theCrookRecipeParsesToItsPlanShapeWithoutATithe() {
    Material hoe = material("IRON_HOE");
    Material shard = material("AMETHYST_SHARD");
    Material redstone = material("REDSTONE");
    Material copper = material("COPPER_INGOT");
    ItemStack crook = result("crook");

    ConfiguredRecipeParser.Result parsed = parser.parse(config.crookRecipe);

    assertThat(parsed.problems()).isEmpty();
    AdaptRecipe.Shaped shaped = parsed.builder().key("test").build();
    assertThat(shaped.getShapes()).containsExactly(" A ", "RHR", " C ");
    assertThat(choice(shaped, 'H')).containsExactly(hoe);
    assertThat(choice(shaped, 'A')).containsExactly(shard);
    assertThat(choice(shaped, 'R')).containsExactly(redstone);
    assertThat(choice(shaped, 'C')).containsExactly(copper);
    assertThat(shaped.getResult()).isSameAs(crook);
    assertThat(config.crookRecipe.getAmount()).isEqualTo(1);
    assertThat(config.crookRecipe.getTithe()).isZero();
  }

  @Test
  void theBlankCoreRecipeParsesToItsPlanShapeWithAOneLevelTithe() {
    Material shard = material("AMETHYST_SHARD");
    Material redstone = material("REDSTONE");
    Material pearl = material("ENDER_PEARL");
    ItemStack core = result("blank_core");

    ConfiguredRecipeParser.Result parsed = parser.parse(config.blankCoreRecipe);

    assertThat(parsed.problems()).isEmpty();
    AdaptRecipe.Shaped shaped = parsed.builder().key("test").build();
    assertThat(shaped.getShapes()).containsExactly("ARA", "RPR", "ARA");
    assertThat(choice(shaped, 'A')).containsExactly(shard);
    assertThat(choice(shaped, 'R')).containsExactly(redstone);
    assertThat(choice(shaped, 'P')).containsExactly(pearl);
    assertThat(shaped.getResult()).isSameAs(core);
    assertThat(config.blankCoreRecipe.getAmount()).isEqualTo(1);
    assertThat(config.blankCoreRecipe.getTithe()).isEqualTo(1);
  }

  @Test
  void bothRecipesRegisterUnderGolemancyKeysTheItemGuardKnows() {
    material("IRON_HOE");
    material("AMETHYST_SHARD");
    material("REDSTONE");
    material("COPPER_INGOT");
    material("ENDER_PEARL");
    result("crook");
    result("blank_core");
    NamespacedKey crookKey = new NamespacedKey("adapt", GolemAnimusBinding.CROOK_RECIPE);
    NamespacedKey coreKey = new NamespacedKey("adapt", GolemAnimusBinding.BLANK_CORE_RECIPE);

    assertThat(parser.builder(crookKey, config.crookRecipe).orElseThrow().build().getKey()).isEqualTo("golemancy-crook");
    assertThat(parser.builder(coreKey, config.blankCoreRecipe).orElseThrow().build().getKey()).isEqualTo("golemancy-blank-core");
    assertThat(book.satisfiedBy(crookKey, List.of())).isTrue();
    assertThat(book.satisfiedBy(coreKey, List.of())).isTrue();
  }

  private static List<Integer> perLevel(IntUnaryOperator value) {
    return IntStream.rangeClosed(1, 5).map(value).boxed().toList();
  }

  private Material material(String name) {
    Material material = mock(Material.class);
    when(material.isItem()).thenReturn(true);
    when(material.name()).thenReturn(name);
    materials.put(name, material);
    return material;
  }

  private ItemStack result(String id) {
    ItemStack template = mock(ItemStack.class);
    ItemStack copy = mock(ItemStack.class);
    when(template.clone()).thenReturn(copy);
    results.put(new NamespacedKey("golemancy", id), template);
    return copy;
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
