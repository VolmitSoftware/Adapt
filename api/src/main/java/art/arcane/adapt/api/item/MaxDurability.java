package art.arcane.adapt.api.item;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.ApiStatus;

/**
 * The maximum durability of one stack, raised or lowered by an ability. Platforms with per-stack item limits store it
 * on the stack; elsewhere Adapt keeps the wanted maximum in the stack's data and scales the damage the stack takes.
 */
@ApiStatus.Experimental
public final class MaxDurability {
  private MaxDurability() {
  }

  /** True when the platform stores the maximum on the stack itself, so the client shows it. Needs a running core. */
  public static boolean perStack() {
    return Engine.bridge().items().maxDurabilityPerStack();
  }

  /** The maximum durability the stack has now. */
  public static int of(ItemRef item) {
    return Engine.bridge().items().maxDurability(item);
  }

  /** Gives the stack a maximum durability of {@code maxDamage}. */
  public static void set(ItemRef item, int maxDamage) {
    Engine.bridge().items().setMaxDurability(item, maxDamage);
  }
}
