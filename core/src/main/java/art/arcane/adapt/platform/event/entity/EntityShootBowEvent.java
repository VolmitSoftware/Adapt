package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.Objects;

/** Bow or crossbow shot. Mutable: consumeItem, cancelled. */
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

  public LivingRef shooter() {
    return shooter;
  }

  public ItemRef bow() {
    return bow;
  }

  public ItemRef consumable() {
    return consumable;
  }

  public EntityRef projectile() {
    return projectile;
  }

  public Hand hand() {
    return hand;
  }

  /** Draw strength, 0 to 1 for bows. */
  public float force() {
    return force;
  }

  public boolean consumeItem() {
    return consumeItem;
  }

  public void setConsumeItem(boolean consumeItem) {
    this.consumeItem = consumeItem;
  }
}
