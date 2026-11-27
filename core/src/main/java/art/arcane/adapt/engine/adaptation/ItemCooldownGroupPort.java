package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

/**
 * Writes the vanilla use-cooldown component that makes a stack belong to a cooldown group. Installed through
 * {@link art.arcane.adapt.AdaptPorts}; without it stacks are not stamped and only the material overlay works.
 */
public interface ItemCooldownGroupPort {
  ItemCooldownGroupPort NONE = (stack, group, markerSeconds) -> false;

  /** Stamps the group onto the stack unless it already carries it. Returns true when the stack changed. */
  boolean stampGroup(ItemRef stack, Id group, float markerSeconds);
}
