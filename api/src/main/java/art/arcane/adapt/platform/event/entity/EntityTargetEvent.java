package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A mob is about to pick a new target or drop its current one. Cancelling keeps the current target. Mutable: target
 * (null clears), cancelled.
 *
 * <p>On mod loaders, brain-driven mobs (piglins, hoglins, wardens and others) post it when their attack target memory
 * changes, including when the memory is about to expire.
 */
@ApiStatus.AvailableSince("1.0.0")
public class EntityTargetEvent extends CancellableEvent {
  private final EntityRef entity;
  private final Reason reason;
  private LivingRef target;

  public EntityTargetEvent(EntityRef entity, LivingRef target, Reason reason) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.target = target;
    this.reason = reason == null ? Reason.UNKNOWN : reason;
  }

  /** The mob changing its target. */
  public EntityRef entity() {
    return entity;
  }

  /** The new target, or null when the mob drops its target. On Bukkit a non-living target arrives as null. */
  public LivingRef target() {
    return target;
  }

  /** Replaces the new target; null makes the mob drop it. */
  public void setTarget(LivingRef target) {
    this.target = target;
  }

  /** Why the target changes; {@link Reason#UNKNOWN} when the platform cannot tell. */
  public Reason reason() {
    return reason;
  }

  /**
   * Target reasons with Bukkit's names.
   * A {@code switch} over this enum needs a default branch: minor releases add constants.
   */
  public enum Reason {
    CLOSEST_PLAYER,
    CLOSEST_ENTITY,
    TARGET_ATTACKED_ENTITY,
    TARGET_ATTACKED_NEARBY_ENTITY,
    TARGET_ATTACKED_OWNER,
    OWNER_ATTACKED_TARGET,
    FORGOT_TARGET,
    TARGET_DIED,
    TEMPT,
    DEFEND_VILLAGE,
    CUSTOM,
    /** The platform could not tell the reason. */
    UNKNOWN
  }
}
