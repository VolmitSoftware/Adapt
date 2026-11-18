package art.arcane.adapt.platform.recipe;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** One recipe input slot. */
public sealed interface Ingredient {
  /** Any of the listed item types. */
  record AnyOf(Set<Id> types) implements Ingredient {
    public AnyOf {
      types = Set.copyOf(types);
      if (types.isEmpty()) {
        throw new IllegalArgumentException("ingredient needs at least one type");
      }
    }
  }

  /** Any item in an item tag, for example {@code minecraft:wool}. */
  record Tag(Id tag) implements Ingredient {
    public Tag {
      Objects.requireNonNull(tag, "tag");
    }
  }

  /** An exact item including components (custom items). */
  record Exact(ItemRef item) implements Ingredient {
    public Exact {
      Objects.requireNonNull(item, "item");
    }
  }

  /**
   * An item of type {@code base} that passes {@code matches}, for custom items told apart by their data. Platforms
   * that cannot test stacks while matching fall back to {@code example} as an exact ingredient; recipe book
   * displays show {@code example}.
   */
  record Matching(Id base, ItemRef example, Predicate<ItemRef> matches) implements Ingredient {
    public Matching {
      Objects.requireNonNull(base, "base");
      Objects.requireNonNull(example, "example");
      Objects.requireNonNull(matches, "matches");
    }
  }

  static Ingredient of(Id type) {
    return new AnyOf(Set.of(type));
  }
}
