package art.arcane.adapt.engine.integration;

import art.arcane.adapt.platform.item.ItemRef;

import java.util.List;
import java.util.Optional;

/**
 * React super stacks: bundles the React plugin marks as one compressed stack. Only Bukkit installs a real port, and
 * only while React is enabled; elsewhere no stack is a super stack.
 */
public interface ReactSuperStackPort {
  ReactSuperStackPort NONE = new ReactSuperStackPort() {
    @Override
    public boolean isSuperStack(ItemRef stack) {
      return false;
    }

    @Override
    public List<ItemRef> items(ItemRef stack) {
      return List.of();
    }

    @Override
    public Optional<ItemRef> withItems(ItemRef stack, List<ItemRef> items) {
      return Optional.empty();
    }
  };

  /** True for a bundle React marks as a super stack, at any amount. */
  boolean isSuperStack(ItemRef stack);

  /** Copies of the non-empty items inside a super stack; empty for anything else. */
  List<ItemRef> items(ItemRef stack);

  /** A copy of the super stack holding these items instead, or empty when it cannot be rebuilt as a super stack. */
  Optional<ItemRef> withItems(ItemRef stack, List<ItemRef> items);
}
