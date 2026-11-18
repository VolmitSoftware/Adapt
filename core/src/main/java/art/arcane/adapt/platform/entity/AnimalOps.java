package art.arcane.adapt.platform.entity;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Breeding, shearing and husbandry state of animal entities. Every per-entity read returns the neutral value
 * (false, 0, null, empty) and every write does nothing when the entity is not of the kind the method describes.
 */
public interface AnimalOps {
  /** True for an animal entity type that is not an enemy (Bukkit Animals and not Enemy; hoglins are excluded). */
  boolean isPeacefulAnimalType(Id entityType);

  /** Ageable adult (Bukkit Ageable#isAdult); false for babies and for entities that do not age. */
  boolean isAdult(EntityRef entity);

  /** Ageable age in ticks: negative for babies, positive while on breeding cooldown, 0 when ready. */
  int age(EntityRef entity);

  void setAge(EntityRef entity, int age);

  /** Bukkit Breedable#canBreed: an ageable animal whose age allows breeding. */
  boolean canBreed(EntityRef entity);

  boolean isInLove(EntityRef entity);

  /** Player whose feeding put the animal in love, or null when nobody did. */
  UUID loveCause(EntityRef entity);

  /** Puts the animal in love for the given ticks and clears its love cause. */
  void setInLove(EntityRef entity, int ticks);

  /** True when the animal can be bred with the given item. */
  boolean isBreedItem(EntityRef entity, Id item);

  boolean isSheared(EntityRef entity);

  void setSheared(EntityRef entity, boolean sheared);

  /** Wool colour of a sheep, or null for other entities. */
  DyeColor woolColor(EntityRef entity);

  /** True for a mooshroom that will give a suspicious stew effect when milked with a bowl. */
  boolean hasStewEffect(EntityRef entity);

  boolean isLeashed(EntityRef entity);

  boolean isTamed(EntityRef entity);

  /** True for a donkey, mule or llama that carries a chest. */
  boolean carriesChest(EntityRef entity);

  /** One roll of the entity's loot table, or an empty list when it has none. */
  List<ItemRef> rollLoot(EntityRef entity, Random random);

  /**
   * One roll of the entity's loot table as if {@code killer} had killed it, with {@code luck} added to the roll.
   * Empty when the entity has no loot table.
   */
  List<ItemRef> rollLoot(EntityRef entity, PlayerRef killer, float luck, Random random);

  /** True when mobs drop loot in the world (the mob loot game rule). */
  boolean mobLoot(WorldRef world);

  /** True for a raider whose raid is still ongoing. */
  boolean inActiveRaid(EntityRef entity);

  /**
   * Tames an untamed tameable entity to the player through the platform's tame event. False when the entity cannot be
   * tamed, is already tamed or the event was cancelled.
   */
  boolean tame(EntityRef entity, PlayerRef owner);

  /** Sitting state of a sittable entity (tamed pets, foxes, pandas, camels); false for others. */
  boolean isSitting(EntityRef entity);

  /** Makes a sittable entity sit or stand; no-op for others. */
  void setSitting(EntityRef entity, boolean sitting);

  /** Wolf anger (Bukkit Wolf#setAngry); no-op for other entities. */
  void setAngry(EntityRef entity, boolean angry);

  /** Collar colour of a wolf or cat, or null for other entities. */
  DyeColor collarColor(EntityRef entity);

  /** Strider shivering (cold, slowed) state; no-op for other entities. */
  void setShivering(EntityRef entity, boolean shivering);

  /** Villager head shake (refusal animation and sound); no-op for other entities. */
  void shakeHead(EntityRef entity);

  /** Mob aggressive flag (attack pose of zombies, arms of illagers and similar); no-op for other entities. */
  void setAggressive(EntityRef entity, boolean aggressive);

  /** Entity a warden is most angry at, or null for other entities and calm wardens. */
  LivingRef angryAt(EntityRef warden);

  /** Removes a warden's anger at {@code target}; no-op for other entities. */
  void clearAnger(EntityRef warden, EntityRef target);
}
