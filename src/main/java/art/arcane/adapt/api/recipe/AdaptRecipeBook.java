package art.arcane.adapt.api.recipe;

import art.arcane.adapt.api.adaptation.Adaptation;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

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
    Set<NamespacedKey> discover = new LinkedHashSet<>();
    Set<NamespacedKey> undiscover = new LinkedHashSet<>();
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

  public static void synchronize(Player player, Plan plan) {
    if (player == null || plan == null || !player.isOnline()) {
      return;
    }

    if (!plan.undiscover().isEmpty()) {
      player.undiscoverRecipes(plan.undiscover());
    }
    if (!plan.discover().isEmpty()) {
      player.discoverRecipes(plan.discover());
    }
  }

  public record Unlock(NamespacedKey key, Adaptation<?> adaptation, int requiredLevel) {
    public Unlock {
      Objects.requireNonNull(key);
      Objects.requireNonNull(adaptation);
      requiredLevel = Math.max(1, requiredLevel);
    }
  }

  public record Plan(List<NamespacedKey> discover, List<NamespacedKey> undiscover) {
    public Plan {
      discover = List.copyOf(discover);
      undiscover = List.copyOf(undiscover);
    }
  }
}
