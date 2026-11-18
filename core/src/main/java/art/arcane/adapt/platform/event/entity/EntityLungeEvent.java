package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.CancellableEvent;

import java.util.Objects;

/** An entity lunges with a spear. Mutable: power, cancelled. */
public class EntityLungeEvent extends CancellableEvent {
  private final LivingRef entity;
  private int power;

  public EntityLungeEvent(LivingRef entity, int power) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.power = power;
  }

  public LivingRef entity() {
    return entity;
  }

  public int power() {
    return power;
  }

  public void setPower(int power) {
    this.power = power;
  }
}
