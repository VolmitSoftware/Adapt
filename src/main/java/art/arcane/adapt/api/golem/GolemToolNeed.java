package art.arcane.adapt.api.golem;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public record GolemToolNeed(String id, Predicate<ItemStack> suits, ToIntFunction<ItemStack> cost, GolemToolNote note) {
  public GolemToolNeed {
    Objects.requireNonNull(id);
    Objects.requireNonNull(suits);
    Objects.requireNonNull(cost);
    Objects.requireNonNull(note);
  }
}
