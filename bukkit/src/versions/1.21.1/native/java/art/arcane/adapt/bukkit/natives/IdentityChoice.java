package art.arcane.adapt.bukkit.natives;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * A recipe choice that accepts any stack passing {@code matches}, for custom items told apart by their data rather than
 * by their exact components (a renamed custom item still counts). These servers have no predicate recipe choice, so it
 * is an exact choice of {@code representative} to everything that only knows the Bukkit API: the client is sent the
 * representative. {@link ServerRecipes#withIdentity} registers crafting recipes holding it so the server tests stacks
 * with {@code matches}.
 */
public final class IdentityChoice extends RecipeChoice.ExactChoice {
  private final Predicate<ItemStack> matches;

  public IdentityChoice(ItemStack representative, Predicate<ItemStack> matches) {
    super(representative);
    this.matches = Objects.requireNonNull(matches, "matches");
  }

  @Override
  public boolean test(ItemStack item) {
    return item != null && matches.test(item);
  }

  @Override
  public IdentityChoice clone() {
    return (IdentityChoice) super.clone();
  }
}
