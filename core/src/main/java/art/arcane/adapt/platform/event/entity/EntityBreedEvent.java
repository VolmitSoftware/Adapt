package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.Objects;

/** Two animals breed. Mutable: experience, cancelled. */
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

  public LivingRef child() {
    return child;
  }

  public LivingRef mother() {
    return mother;
  }

  public LivingRef father() {
    return father;
  }

  /** Entity that caused the breeding (usually a player), or null. */
  public LivingRef breeder() {
    return breeder;
  }

  public ItemRef bredWith() {
    return bredWith;
  }

  public int experience() {
    return experience;
  }

  public void setExperience(int experience) {
    this.experience = Math.max(0, experience);
  }
}
