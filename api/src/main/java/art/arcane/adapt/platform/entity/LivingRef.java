package art.arcane.adapt.platform.entity;

import art.arcane.adapt.platform.event.entity.EntityRegainHealthEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;
import java.util.List;

/** Living entity: health, attributes, potion effects and equipment. Same rules as {@link EntityRef}. */
@ApiStatus.AvailableSince("1.0.0")
public interface LivingRef extends EntityRef {
  /** Current health points; two points make one heart. */
  double health();

  /** Sets health directly, clamped to 0 and {@link #maxHealth()}. */
  void setHealth(double health);

  /** Value of the max health attribute. */
  double maxHealth();

  /** Absorption points (the golden hearts) on top of health. */
  double absorption();

  /** Sets the absorption points. */
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

  /**
   * Adds health, capped at max health. Use {@link #regainHealth(double, EntityRegainHealthEvent.Reason)} when the
   * regain should carry a reason and report the amount gained.
   */
  void heal(double amount);

  /** Ticks left in the damage immunity window that follows a hit. */
  int noDamageTicks();

  /** Sets the ticks left in the damage immunity window; 0 lets the next hit land in full. */
  void setNoDamageTicks(int ticks);

  /** Sets the damage of the last hit, which a new hit inside the no-damage window must exceed. */
  void setLastHurtAmount(double amount);

  /** Eye position with head yaw and pitch, the origin for line-of-sight checks. */
  Loc eyeLocation();

  /** True while the entity is in the swimming pose. */
  boolean isSwimming();

  /** Starts or stops the swimming pose. */
  void setSwimming(boolean swimming);

  /** Remaining air supply in ticks. */
  int remainingAir();

  /** Sets the remaining air supply in ticks. */
  void setRemainingAir(int ticks);

  /** Maximum air supply in ticks. */
  int maximumAir();

  /** True while the entity glides with an elytra. */
  boolean isGliding();

  /** Starts or stops elytra gliding. */
  void setGliding(boolean gliding);

  /** True while the entity is on a climbable block such as a ladder or vine. */
  boolean isClimbing();

  /** True while the entity is using an item: drawing a bow, eating, raising a shield. */
  boolean isHandRaised();

  /** True when the entity is invisible to players. */
  boolean isInvisible();

  /** Makes the entity invisible or visible to players. */
  void setInvisible(boolean invisible);

  /** False when the mob's AI is off and it stands still (the vanilla NoAI flag). */
  boolean hasAi();

  /** Turns the mob's AI on or off. */
  void setAi(boolean ai);

  /** Killer of this entity in the current or last death, or null. */
  PlayerRef killer();

  /** Attribute value including modifiers, or NaN when the entity lacks the attribute. */
  double attributeValue(Id attribute);

  /** Base attribute value, or NaN when absent. */
  double attributeBase(Id attribute);

  /** Sets the base value of the attribute, for example {@code minecraft:max_health}; no-op when the entity lacks it. */
  void setAttributeBase(Id attribute, double value);

  /** True when the entity has the attribute. */
  boolean hasAttribute(Id attribute);

  /** Adds or replaces the modifier with the same key, saved with the entity. Returns false when the attribute is absent. */
  boolean setModifier(Id attribute, AttributeMod modifier);

  /**
   * Adds or replaces the modifier with the same key without saving it with the entity: it is gone after the
   * entity unloads or the server restarts. Returns false when the attribute is absent.
   */
  boolean setTransientModifier(Id attribute, AttributeMod modifier);

  /** Removes the modifier with {@code modifierKey} from the attribute; no-op when either is absent. */
  void removeModifier(Id attribute, Id modifierKey);

  /** The modifier with {@code modifierKey} on the attribute, or null when either is absent. */
  AttributeMod modifier(Id attribute, Id modifierKey);

  /** Every modifier on the attribute, saved and transient; empty when the entity lacks the attribute. */
  List<AttributeMod> modifiers(Id attribute);

  /** Adds or replaces the effect of the same type. False when the effect type is unknown. */
  boolean addEffect(EffectInstance effect);

  /** The active effect of the type, or null when none is active or the type is unknown. */
  EffectInstance effect(Id type);

  /** True when an effect of the type is active. */
  boolean hasEffect(Id type);

  /** Removes the active effect of the type; no-op when none is active. */
  void removeEffect(Id type);

  /** Every active effect, as values. */
  Collection<EffectInstance> effects();

  /** Item in the equipment slot; empty when the slot is empty or the entity has no such slot. */
  ItemRef equipment(EquipmentSlot slot);

  /** Puts a copy of {@code item} in the equipment slot; null or empty clears it. */
  void setEquipment(EquipmentSlot slot, ItemRef item);

  /** Drop chance of the item in an equipment slot when the entity dies; no-op where the entity keeps none. */
  void setEquipmentDropChance(EquipmentSlot slot, float chance);

  /** Damages the item in the slot like vanilla use (Unbreaking, item damage event, breaking). */
  void damageEquipment(EquipmentSlot slot, int amount);

  /** Plays the vanilla break sound and particles of the item in the slot to nearby players. */
  void playEquipmentBreak(EquipmentSlot slot);

  /** True when the entity picks up items it walks over. */
  boolean canPickupItems();

  /** Lets the entity pick up items it walks over, or stops it. */
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

  /** True when no block blocks the view from this entity's eyes to {@code other}. */
  boolean hasLineOfSight(EntityRef other);

  /** Plays the arm swing animation of the hand for nearby players. */
  void swingHand(Hand hand);

  /** Damage immunity ticks granted after a hit (vanilla 20). */
  int maxNoDamageTicks();

  /** Item currently being used (drawn bow, raised shield, food being eaten); empty when none. */
  ItemRef activeItem();
}
