package art.arcane.adapt.content.golem;

import art.arcane.adapt.platform.item.ItemRef;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public record GolemToolNeed(String id, Predicate<ItemRef> suits, ToIntFunction<ItemRef> cost, GolemToolNote note) {
  public GolemToolNeed {
    Objects.requireNonNull(id);
    Objects.requireNonNull(suits);
    Objects.requireNonNull(cost);
    Objects.requireNonNull(note);
  }
}
