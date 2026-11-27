package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.item.ExactIngredient;
import art.arcane.adapt.api.item.ExactItemResolver;
import art.arcane.adapt.api.item.RecipeItemCatalog;
import art.arcane.adapt.api.item.ShapedRecipeParser;
import art.arcane.adapt.content.golem.GolemBody;
import art.arcane.adapt.content.golem.item.ExactIngredientBook;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GolemAnimusBindingConfigTest {
  private final GolemAnimusBinding.Config config = new GolemAnimusBinding.Config();
  private final Map<String, Id> materials = new HashMap<>();
  private final Map<Id, ItemRef> results = new HashMap<>();
  private final ExactIngredientBook book = GolemItems.recipeBook();
  private final ShapedRecipeParser parser = new ShapedRecipeParser(new FakeCatalog(), new FakeResolver());

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
  void theCrookRecipeParsesToItsPlanShape() {
    Id hoe = material("IRON_HOE");
    Id shard = material("AMETHYST_SHARD");
    Id redstone = material("REDSTONE");
    Id copper = material("COPPER_INGOT");
    ItemRef crook = result("crook");

    ShapedRecipeParser.Result parsed = parser.parse(config.crookRecipe.toConfig());

    assertThat(parsed.problems()).isEmpty();
    RecipeSpec.Shaped shaped = parsed.draft().key(Id.adapt("test"));
    assertThat(shaped.shape()).containsExactly(" A ", "RHR", " C ");
    assertThat(choice(shaped, 'H')).containsExactly(hoe);
    assertThat(choice(shaped, 'A')).containsExactly(shard);
    assertThat(choice(shaped, 'R')).containsExactly(redstone);
    assertThat(choice(shaped, 'C')).containsExactly(copper);
    assertThat(shaped.result()).isSameAs(crook);
    assertThat(config.crookRecipe.getAmount()).isEqualTo(1);
  }

  @Test
  void theBlankCoreRecipeParsesToItsPlanShape() {
    Id shard = material("AMETHYST_SHARD");
    Id redstone = material("REDSTONE");
    Id pearl = material("ENDER_PEARL");
    ItemRef core = result("blank_core");

    ShapedRecipeParser.Result parsed = parser.parse(config.blankCoreRecipe.toConfig());

    assertThat(parsed.problems()).isEmpty();
    RecipeSpec.Shaped shaped = parsed.draft().key(Id.adapt("test"));
    assertThat(shaped.shape()).containsExactly("ARA", "RPR", "ARA");
    assertThat(choice(shaped, 'A')).containsExactly(shard);
    assertThat(choice(shaped, 'R')).containsExactly(redstone);
    assertThat(choice(shaped, 'P')).containsExactly(pearl);
    assertThat(shaped.result()).isSameAs(core);
    assertThat(config.blankCoreRecipe.getAmount()).isEqualTo(1);
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
    Id crookKey = Id.adapt(GolemAnimusBinding.CROOK_RECIPE);
    Id coreKey = Id.adapt(GolemAnimusBinding.BLANK_CORE_RECIPE);

    assertThat(book.register(parser, crookKey, config.crookRecipe).orElseThrow().key()).isEqualTo(Id.adapt("golemancy-crook"));
    assertThat(book.register(parser, coreKey, config.blankCoreRecipe).orElseThrow().key()).isEqualTo(Id.adapt("golemancy-blank-core"));
    assertThat(book.satisfiedBy(crookKey, List.of())).isTrue();
    assertThat(book.satisfiedBy(coreKey, List.of())).isTrue();
  }

  private static List<Integer> perLevel(IntUnaryOperator value) {
    return IntStream.rangeClosed(1, 5).map(value).boxed().toList();
  }

  private Id material(String name) {
    Id material = Id.minecraft(name.toLowerCase(Locale.ROOT));
    materials.put(name, material);
    return material;
  }

  private ItemRef result(String id) {
    ItemRef template = mock(ItemRef.class);
    ItemRef copy = mock(ItemRef.class);
    when(template.copyWithAmount(anyInt())).thenReturn(copy);
    results.put(Id.of("golemancy", id), template);
    return copy;
  }

  private static Set<Id> choice(RecipeSpec.Shaped shaped, char symbol) {
    Ingredient choice = shaped.ingredients().get(symbol);
    assertThat(choice).isInstanceOf(Ingredient.AnyOf.class);
    return ((Ingredient.AnyOf) choice).types();
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
      return null;
    }

    @Override
    public int maxStackSize(Id type) {
      return 64;
    }
  }

  private final class FakeResolver implements ExactItemResolver {
    @Override
    public ExactIngredient ingredient(Id id) {
      return null;
    }

    @Override
    public ItemRef result(Id id) {
      return results.get(id);
    }
  }
}
