package art.arcane.adapt.api.attribute;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

/**
 * Attribute modifiers an adaptation puts on a living entity. Each modifier is keyed by the adaptation's name and a
 * slot, so applying the same slot again replaces it. Modifiers are removed when the adaptation is unlearned or
 * unregistered. Attributes the server lacks are emulated.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface AdaptAttributeService {
  /** The running core's service. */
  static AdaptAttributeService get() {
    return Engine.bridge().world().attributes();
  }

  /**
   * Puts a modifier on the target, replacing the one with the same adaptation name, slot and attribute. Callable
   * from any thread: the change runs on the target's thread. A non-finite amount or a null operation is ignored.
   */
  void apply(LivingRef target, String adaptationName, String slot, Id attribute, double amount,
             AttributeOperation operation);

  /** As {@link #apply}, removed again after {@code durationTicks}. */
  void applyTimed(LivingRef target, String adaptationName, String slot, Id attribute, double amount,
                  AttributeOperation operation, long durationTicks);

  /** Removes one modifier {@link #apply} put on; runs on the target's thread. */
  void remove(LivingRef target, String adaptationName, String slot, Id attribute);

  /** Removes every modifier of the adaptation from the target. */
  void removeAll(LivingRef target, String adaptationName);

  /**
   * Removes every modifier Adapt put on the target.
   *
   * @return how many were removed
   */
  int clearAllAdapt(LivingRef target);

  /** The attribute value with Adapt's modifiers, emulated when the server lacks the attribute; NaN when unknown. */
  double value(LivingRef target, Id attribute);

  /** True when the server lacks the attribute and Adapt emulates it. */
  boolean isEmulated(Id attribute);

  /** The modifier key {@link #apply} uses for an adaptation's slot, for reading that modifier off an entity. */
  Id modifierKey(String adaptationName, String slot);
}
