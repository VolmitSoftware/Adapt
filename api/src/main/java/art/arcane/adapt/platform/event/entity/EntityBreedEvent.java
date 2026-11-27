package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * Two animals are about to produce a child. Cancelling stops the birth. The experience orb the breeding drops uses
 * {@link #experience()}.
 */
@ApiStatus.AvailableSince("1.0.0")
public class EntityBreedEvent extends CancellableEvent {
  private final LivingRef child;
  private final LivingRef mother;
  private final LivingRef father;
  private final LivingRef breeder;
  private final ItemRef bredWith;
  private int experience;

  public EntityBreedEvent(LivingRef child, LivingRef mother, LivingRef father, LivingRef breeder, ItemRef bredWith, int experience) {
    this.child = Objects.requireNonNull(child, "child");
    this.mother = Objects.requireNonNull(mother, "mother");
    this.father = Objects.requireNonNull(father, "father");
    this.breeder = breeder;
    this.bredWith = bredWith;
    this.experience = experience;
  }

  /** The baby about to be born. */
  public LivingRef child() {
    return child;
  }

  /** One parent. Mother and father are the two parents in the order the platform reports them, not by sex. */
  public LivingRef mother() {
    return mother;
  }

  /** The other parent. */
  public LivingRef father() {
    return father;
  }

  /** Entity that caused the breeding (usually a player), or null. */
  public LivingRef breeder() {
    return breeder;
  }

  /** The item that made the parents breed, or null when unknown. Mod loaders always report null. */
  public ItemRef bredWith() {
    return bredWith;
  }

  /** Experience the breeding orb drops. */
  public int experience() {
    return experience;
  }

  /** Changes the orb's experience; negative values become 0. */
  public void setExperience(int experience) {
    this.experience = Math.max(0, experience);
  }
}
