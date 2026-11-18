package art.arcane.adapt.api.recipe;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.recipe.RecipeHost;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToIntFunction;

public final class AdaptRecipeBook {
  private AdaptRecipeBook() {
  }

  public static Plan plan(Collection<Unlock> registeredRecipes,
                          ToIntFunction<Adaptation<?>> levelResolver) {
    Set<Id> discover = new LinkedHashSet<>();
    Set<Id> undiscover = new LinkedHashSet<>();
    if (registeredRecipes == null || registeredRecipes.isEmpty()) {
      return new Plan(List.of(), List.of());
    }

    Objects.requireNonNull(levelResolver);
    Map<Adaptation<?>, Integer> levels = new IdentityHashMap<>();
    for (Unlock unlock : registeredRecipes) {
      if (unlock == null) {
        continue;
      }

      Adaptation<?> adaptation = unlock.adaptation();
      Integer level = levels.get(adaptation);
      if (level == null) {
        level = levelResolver.applyAsInt(adaptation);
        levels.put(adaptation, level);
      }

      if (level >= unlock.requiredLevel()) {
        discover.add(unlock.key());
      } else {
        undiscover.add(unlock.key());
      }
    }
    undiscover.removeAll(discover);
    return new Plan(new ArrayList<>(discover), new ArrayList<>(undiscover));
  }

  public static void synchronize(PlayerRef player, Plan plan) {
    if (player == null || plan == null || !player.isOnline()) {
      return;
    }

    RecipeHost recipes = AdaptCore.platform().recipes();
    for (Id key : plan.undiscover()) {
      recipes.undiscover(player, key);
    }
    for (Id key : plan.discover()) {
      recipes.discover(player, key);
    }
  }

  public record Unlock(Id key, Adaptation<?> adaptation, int requiredLevel) {
    public Unlock {
      Objects.requireNonNull(key);
      Objects.requireNonNull(adaptation);
      requiredLevel = Math.max(1, requiredLevel);
    }
  }

  public record Plan(List<Id> discover, List<Id> undiscover) {
    public Plan {
      discover = List.copyOf(discover);
      undiscover = List.copyOf(undiscover);
    }
  }
}
