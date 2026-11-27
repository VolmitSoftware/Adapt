package art.arcane.adapt.platform.recipe;

import org.jetbrains.annotations.ApiStatus;

/** A recipe registered through {@link RecipeHost#register}; keep it to remove the recipe. */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface RecipeRegistration {
  /** Removes the recipe and resends recipe books. Safe to call twice. */
  void remove();
}
