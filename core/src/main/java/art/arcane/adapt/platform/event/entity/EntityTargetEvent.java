package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.CancellableEvent;

import java.util.Objects;

/** A mob picks or drops a target. Mutable: target (null clears), cancelled. */
public class EntityTargetEvent extends CancellableEvent {
  private final EntityRef entity;
  private final Reason reason;
  private LivingRef target;

  public EntityTargetEvent(EntityRef entity, LivingRef target, Reason reason) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.target = target;
    this.reason = reason == null ? Reason.UNKNOWN : reason;
  }

  public EntityRef entity() {
    return entity;
  }

  public LivingRef target() {
    return target;
  }

  public void setTarget(LivingRef target) {
    this.target = target;
  }

  public Reason reason() {
    return reason;
  }

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
    UNKNOWN
  }
}
