package art.arcane.adapt.content.adaptation.brewing;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.potion.BrewingRecipe;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The brews the brewing skill's potion adaptations unlock. An upgrade that unlocks several of them registers the very
 * recipe objects those adaptations built, so the brewing stand matches one recipe whichever of them the brewer holds.
 */
public final class BrewingBrews {
  public static final List<String> HOSTILE = List.of("brewing-blindness", "brewing-darkness", "brewing-decay",
      "brewing-fatigue", "brewing-hunger", "brewing-nausea");
  public static final List<String> DEFENSIVE = List.of("brewing-absorption", "brewing-healthboost",
      "brewing-resistance", "brewing-saturation");
  public static final List<String> HASTE = List.of("brewing-haste");

  private BrewingBrews() {
  }

  /** Every recipe the named adaptations of {@code skill} registered, in the order the names are given. */
  public static List<BrewingRecipe> recipesOf(Skill<?> skill, List<String> adaptations) {
    if (skill == null) {
      return List.of();
    }
    List<BrewingRecipe> recipes = new ArrayList<>();
    for (String name : adaptations) {
      original(skill, name).ifPresent((Adaptation<?> adaptation) -> recipes.addAll(adaptation.getBrewingRecipes()));
    }
    return List.copyOf(recipes);
  }

  /** The first recipe of each named adaptation, which is its base strength brew. */
  public static List<BrewingRecipe> baseRecipesOf(Skill<?> skill, List<String> adaptations) {
    if (skill == null) {
      return List.of();
    }
    List<BrewingRecipe> recipes = new ArrayList<>();
    for (String name : adaptations) {
      original(skill, name).map(Adaptation::getBrewingRecipes)
          .filter((List<BrewingRecipe> brewed) -> !brewed.isEmpty())
          .ifPresent((List<BrewingRecipe> brewed) -> recipes.add(brewed.get(0)));
    }
    return List.copyOf(recipes);
  }

  /** The effects the recipes' results carry. */
  public static List<EffectInstance> effectsOf(List<BrewingRecipe> recipes) {
    List<EffectInstance> effects = new ArrayList<>();
    for (BrewingRecipe recipe : recipes) {
      effects.addAll(recipe.getResult().potionContents().map(PotionContents::customEffects).orElse(List.of()));
    }
    return effects;
  }

  public static Set<Id> effectTypesOf(List<BrewingRecipe> recipes) {
    Set<Id> types = new LinkedHashSet<>();
    for (EffectInstance effect : effectsOf(recipes)) {
      types.add(effect.type());
    }
    return types;
  }

  private static Optional<Adaptation<?>> original(Skill<?> skill, String name) {
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (adaptation.getName().equals(name)) {
        return Optional.of(adaptation);
      }
    }
    return Optional.empty();
  }
}
