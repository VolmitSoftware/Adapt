package art.arcane.adapt.platform.entity;

import art.arcane.adapt.platform.event.entity.EntityRegainHealthEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.Collection;
import java.util.List;

/** Living entity: health, attributes, potion effects and equipment. Same rules as {@link EntityRef}. */
public interface LivingRef extends EntityRef {
  double health();

  void setHealth(double health);

  /** Value of the max health attribute. */
  double maxHealth();

  double absorption();

  void setAbsorption(double absorption);

  /** Applies damage as if dealt by {@code source} (may be null); goes through the damage event. */
  void damage(double amount, EntityRef source);

  /**
   * Applies damage of a vanilla damage type (for example {@code minecraft:player_attack}) with no attacker; goes
   * through the damage event. An unknown type applies generic damage.
   */
  void damage(double amount, Id damageType);

  /**
   * Applies damage of a vanilla damage type caused by {@code source} (causing and direct entity; may be null); goes
   * through the damage event. An unknown type falls back to {@link #damage(double, EntityRef)}.
   */
  void damage(double amount, Id damageType, EntityRef source);

  void heal(double amount);

  int noDamageTicks();

  void setNoDamageTicks(int ticks);

  /** Sets the damage of the last hit, which a new hit inside the no-damage window must exceed. */
  void setLastHurtAmount(double amount);

  Loc eyeLocation();

  boolean isSwimming();

  void setSwimming(boolean swimming);

  /** Remaining air supply in ticks. */
  int remainingAir();

  /** Maximum air supply in ticks. */
  int maximumAir();

  boolean isGliding();

  void setGliding(boolean gliding);

  boolean isClimbing();

  /** True while the entity is using an item: drawing a bow, eating, raising a shield. */
  boolean isHandRaised();

  boolean isInvisible();

  void setInvisible(boolean invisible);

  boolean hasAi();

  void setAi(boolean ai);

  /** Killer of this entity in the current or last death, or null. */
  PlayerRef killer();

  /** Attribute value including modifiers, or NaN when the entity lacks the attribute. */
  double attributeValue(Id attribute);

  /** Base attribute value, or NaN when absent. */
  double attributeBase(Id attribute);

  void setAttributeBase(Id attribute, double value);

  boolean hasAttribute(Id attribute);

  /** Adds or replaces the modifier with the same key, saved with the entity. Returns false when the attribute is absent. */
  boolean setModifier(Id attribute, AttributeMod modifier);

  /**
   * Adds or replaces the modifier with the same key without saving it with the entity: it is gone after the
   * entity unloads or the server restarts. Returns false when the attribute is absent.
   */
  boolean setTransientModifier(Id attribute, AttributeMod modifier);

  void removeModifier(Id attribute, Id modifierKey);

  AttributeMod modifier(Id attribute, Id modifierKey);

  /** Every modifier on the attribute, saved and transient; empty when the entity lacks the attribute. */
  List<AttributeMod> modifiers(Id attribute);

  /** Adds or replaces the effect of the same type. */
  boolean addEffect(EffectInstance effect);

  EffectInstance effect(Id type);

  boolean hasEffect(Id type);

  void removeEffect(Id type);

  Collection<EffectInstance> effects();

  ItemRef equipment(EquipmentSlot slot);

  void setEquipment(EquipmentSlot slot, ItemRef item);

  /** Drop chance of the item in an equipment slot when the entity dies; no-op where the entity keeps none. */
  void setEquipmentDropChance(EquipmentSlot slot, float chance);

  /** Damages the item in the slot like vanilla use (Unbreaking, item damage event, breaking). */
  void damageEquipment(EquipmentSlot slot, int amount);

  /** Plays the vanilla break sound and particles of the item in the slot to nearby players. */
  void playEquipmentBreak(EquipmentSlot slot);

  boolean canPickupItems();

  void setCanPickupItems(boolean pickup);

  /** False keeps a mob from despawning when no player is near; no-op for entities that never despawn. */
  void setRemoveWhenFarAway(boolean remove);

  /**
   * Regains health through the platform's regain-health event with {@code reason}, honouring cancellation and a
   * changed amount, clamped to max health. Returns the health actually gained: 0 for a non-positive amount or when
   * dead, at full health or cancelled.
   */
  double regainHealth(double amount, EntityRegainHealthEvent.Reason reason);

  /** The last damage this entity took that was not cancelled, or null when it took none since it loaded. */
  LastDamage lastDamage();

  /** First solid block in the line of sight within range, or null. */
  BlockRef targetBlock(double range);

  /**
   * The last two blocks along the line of sight, walked block by block from the eyes for up to
   * {@code maxDistance} blocks and stopping at the first block that is not air: previous block first, target
   * last. A single element when the target is the first block walked.
   */
  List<BlockRef> lastTwoTargetBlocks(int maxDistance);

  boolean hasLineOfSight(EntityRef other);

  void swingHand(Hand hand);

  /** Damage immunity ticks granted after a hit (vanilla 20). */
  int maxNoDamageTicks();

  /** Item currently being used (drawn bow, raised shield, food being eaten); empty when none. */
  ItemRef activeItem();
}
