package art.arcane.adapt.platform.recipe;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * One input slot of a server recipe as the server reports it: either item types in the server's order, exact
 * stacks compared with {@link ItemRef#isSimilar}, or a platform matcher (a custom item ingredient) shown by an
 * example stack. Two choices are equal when they accept the same listed inputs.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class RecipeChoice {
  private final List<Id> types;
  private final List<ItemRef> exact;
  private final Predicate<ItemRef> matcher;

  private RecipeChoice(List<Id> types, List<ItemRef> exact, Predicate<ItemRef> matcher) {
    this.types = List.copyOf(types);
    this.exact = List.copyOf(exact);
    this.matcher = matcher;
  }

  /** A slot that accepts any of the item types. */
  public static RecipeChoice ofTypes(List<Id> types) {
    return new RecipeChoice(types, List.of(), null);
  }

  /** A slot that accepts stacks similar to any of {@code stacks}. */
  public static RecipeChoice ofExact(List<ItemRef> stacks) {
    return new RecipeChoice(List.of(), stacks, null);
  }

  /** A slot the platform matches itself, such as a custom item ingredient; {@code example} is what it shows. */
  public static RecipeChoice ofMatching(ItemRef example, Predicate<ItemRef> matcher) {
    return new RecipeChoice(List.of(), List.of(Objects.requireNonNull(example, "example")),
        Objects.requireNonNull(matcher, "matcher"));
  }

  /** True when the choice lists exact stacks rather than item types. */
  public boolean isExact() {
    return !exact.isEmpty();
  }

  /** Accepted item types in the server's order; empty for an exact or matcher choice. */
  public List<Id> types() {
    return types;
  }

  /** Accepted exact stacks, or the single example stack of a matcher choice; empty for a type choice. */
  public List<ItemRef> exact() {
    return exact;
  }

  /** The first listed input's type, or null for an empty choice. */
  public Id representativeType() {
    if (isExact()) {
      return exact.get(0).type();
    }
    return types.isEmpty() ? null : types.get(0);
  }

  /** True when the slot accepts {@code item}; false for a null or empty item. */
  public boolean test(ItemRef item) {
    if (item == null || item.isEmpty()) {
      return false;
    }
    if (matcher != null) {
      return matcher.test(item);
    }
    if (isExact()) {
      for (ItemRef stack : exact) {
        if (stack.isSimilar(item)) {
          return true;
        }
      }
      return false;
    }
    return types.contains(item.type());
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof RecipeChoice choice) || !types.equals(choice.types) || exact.size() != choice.exact.size()) {
      return false;
    }
    for (int index = 0; index < exact.size(); index++) {
      ItemRef mine = exact.get(index);
      ItemRef theirs = choice.exact.get(index);
      if (mine.amount() != theirs.amount() || !mine.isSimilar(theirs)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    int hash = types.hashCode();
    for (ItemRef stack : exact) {
      hash = (31 * hash) + Objects.hash(stack.type(), stack.amount());
    }
    return hash;
  }
}
