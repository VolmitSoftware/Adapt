package art.arcane.adapt.client.view;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.engine.skill.LegacyContentNames;

import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.engine.mutation.tool.MutationTools;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.util.common.misc.ItemLooks;
import art.arcane.volmlib.util.localization.LanguageAudience;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/** Builds the {@link ToolView}s a viewer sees, with each tool's name in the viewer's locale. */
public final class ToolViews {
  private ToolViews() {
  }

  /** Every registered mutation tool, in registry order, under the adaptation page that hands it out. */
  public static List<ToolView> build(SkillRegistry registry, UUID viewer) {
    return LanguageAudience.call(viewer, () -> {
      List<ToolView> tools = new ArrayList<>();
      for (MutationTool<?> tool : MutationTools.all(registry)) {
        tools.add(view(tool));
      }
      return List.copyOf(tools);
    });
  }

  static ToolView view(MutationTool<?> tool) {
    List<RecipeSpec> recipes = tool.recipes();
    return new ToolView(page(tool.owner()), tool.item().id().toString(), CatalogViews.plain(MutationTools.name(tool)), icon(tool),
        tool.requiredLevel(), recipes.isEmpty() ? List.of() : ingredients(recipes.get(0)));
  }

  /** The tool's item with its custom model data and its own sprite. */
  public static IconRef icon(MutationTool<?> tool) {
    Id id = tool.item().id();
    return new IconRef(tool.item().baseItem().toString(), ItemLooks.number(LegacyContentNames.of(id)), null, IconSprites.tool(id));
  }

  /** The adaptation whose page shows the tool: a signature's tool shows on the mutated adaptation it replaces. */
  static String page(Adaptation<?> owner) {
    return (owner instanceof Signature<?> signature
        ? signature.getSlot().pair().child(signature.getSlot().adaptationKey()) : owner.getId()).toString();
  }

  /** The recipe's ingredients, each item or tag once with how many the recipe takes, in first-use order. */
  static List<ToolView.Ingredient> ingredients(RecipeSpec recipe) {
    List<Ingredient> slots = new ArrayList<>();
    switch (recipe) {
      case RecipeSpec.Shaped shaped -> {
        for (String row : shaped.shape()) {
          for (char key : row.toCharArray()) {
            Ingredient ingredient = shaped.ingredients().get(key);
            if (ingredient != null) {
              slots.add(ingredient);
            }
          }
        }
      }
      case RecipeSpec.Shapeless shapeless -> slots.addAll(shapeless.ingredients());
      case RecipeSpec.Cooking cooking -> slots.add(cooking.input());
      case RecipeSpec.Stonecutting cutting -> slots.add(cutting.input());
      case RecipeSpec.Smithing smithing -> slots.addAll(List.of(smithing.template(), smithing.base(), smithing.addition()));
    }
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (Ingredient slot : slots) {
      counts.merge(itemId(slot), 1, Integer::sum);
    }
    List<ToolView.Ingredient> ingredients = new ArrayList<>(counts.size());
    counts.forEach((String itemId, Integer count) -> ingredients.add(new ToolView.Ingredient(itemId, count)));
    return List.copyOf(ingredients);
  }

  private static String itemId(Ingredient ingredient) {
    return switch (ingredient) {
      case Ingredient.AnyOf any -> new TreeSet<>(any.types().stream().map(Object::toString).toList()).first();
      case Ingredient.Tag tag -> "#" + tag.tag();
      case Ingredient.Exact exact -> String.valueOf(exact.item().type());
      case Ingredient.Matching matching -> matching.base().toString();
    };
  }
}
