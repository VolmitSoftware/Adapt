package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A bow, crossbow or other projectile weapon is about to fire one projectile. Cancelling stops the shot. Mutable:
 * consumeItem, cancelled.
 */
@ApiStatus.AvailableSince("1.0.0")
public class EntityShootBowEvent extends CancellableEvent {
  private final LivingRef shooter;
  private final ItemRef bow;
  private final ItemRef consumable;
  private final EntityRef projectile;
  private final Hand hand;
  private final float force;
  private boolean consumeItem;

  public EntityShootBowEvent(LivingRef shooter, ItemRef bow, ItemRef consumable, EntityRef projectile, Hand hand,
                             float force, boolean consumeItem) {
    this.shooter = Objects.requireNonNull(shooter, "shooter");
    this.bow = Objects.requireNonNull(bow, "bow");
    this.consumable = consumable;
    this.projectile = Objects.requireNonNull(projectile, "projectile");
    this.hand = hand == null ? Hand.MAIN_HAND : hand;
    this.force = force;
    this.consumeItem = consumeItem;
  }

  /** The entity firing. */
  public LivingRef shooter() {
    return shooter;
  }

  /** The weapon firing. */
  public ItemRef bow() {
    return bow;
  }

  /** The ammunition being fired, or null when the platform reports none. */
  public ItemRef consumable() {
    return consumable;
  }

  /** The projectile about to be launched. */
  public EntityRef projectile() {
    return projectile;
  }

  /** Hand holding the weapon; the main hand when the platform does not say. */
  public Hand hand() {
    return hand;
  }

  /** Draw strength, 0 to 1 for bows. Mod loaders report 1 for other weapons. */
  public float force() {
    return force;
  }

  /** Whether the shot uses up the ammunition. */
  public boolean consumeItem() {
    return consumeItem;
  }

  /** False keeps the ammunition. On mod loaders the arrow can then be picked up only in creative mode. */
  public void setConsumeItem(boolean consumeItem) {
    this.consumeItem = consumeItem;
  }
}
