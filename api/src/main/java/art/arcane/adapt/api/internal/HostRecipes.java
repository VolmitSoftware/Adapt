package art.arcane.adapt.api.internal;

import art.arcane.adapt.platform.recipe.RecipeHost;
import art.arcane.adapt.platform.recipe.RecipeRegistration;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Optional;

/** The recipe host a platform implements: what addons see, plus what only the engine may do. */
@ApiStatus.Internal
public interface HostRecipes extends RecipeHost {
  /** Registers or replaces the recipe. Returns false when the platform rejected it. */
  boolean add(RecipeSpec recipe);

  /** Removes a recipe registered through this host; nothing happens for an unknown key. */
  void unregister(Id key);

  /** Registers the recipe, refusing a key this host already has, so only the engine replaces recipes. */
  @Override
  default Optional<RecipeRegistration> register(RecipeSpec recipe) {
    Id key = recipe.key();
    if (registered().contains(key)) {
      throw new IllegalArgumentException("Recipe " + key + " is already registered");
    }
    if (!add(recipe)) {
      return Optional.empty();
    }
    return Optional.of(new RecipeRegistration() {
      private boolean removed;

      @Override
      public synchronized void remove() {
        if (!removed) {
          removed = true;
          unregister(key);
        }
      }
    });
  }
}
