package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * An entity's spear lunge is about to push it forward. Cancelling stops the push. Mutable: power, cancelled.
 *
 * <p>On Bukkit servers only Paper 26.x posts it.
 */
@ApiStatus.AvailableSince("1.0.0")
public class EntityLungeEvent extends CancellableEvent {
  private final LivingRef entity;
  private int power;

  public EntityLungeEvent(LivingRef entity, int power) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.power = power;
  }

  /** The entity lunging. */
  public LivingRef entity() {
    return entity;
  }

  /** Lunge enchantment level that sets the strength of the push. */
  public int power() {
    return power;
  }

  /** Changes the level the push uses. */
  public void setPower(int power) {
    this.power = power;
  }
}
