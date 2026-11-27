package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A living entity other than a player is about to be added to the world. Cancelling stops the spawn, so the entity is
 * never added.
 */
@ApiStatus.AvailableSince("1.0.0")
public class CreatureSpawnEvent extends CancellableEvent {
  private final LivingRef entity;
  private final Loc location;
  private final Reason reason;

  public CreatureSpawnEvent(LivingRef entity, Loc location, Reason reason) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.location = Objects.requireNonNull(location, "location");
    this.reason = reason == null ? Reason.DEFAULT : reason;
  }

  /** The entity being spawned, not yet in the world. */
  public LivingRef entity() {
    return entity;
  }

  /** Where the entity appears. */
  public Loc location() {
    return location;
  }

  /** Why the entity spawned; {@link Reason#DEFAULT} when the platform cannot tell. */
  public Reason reason() {
    return reason;
  }

  /**
   * Spawn reasons with Paper's names. Mod loaders map vanilla's spawn reason and its context onto these, falling back
   * to {@link #DEFAULT}.
   * A {@code switch} over this enum needs a default branch: minor releases add constants.
   */
  public enum Reason {
    NATURAL,
    /** The rider of a jockey, such as a chicken jockey. */
    JOCKEY,
    CHUNK_GEN,
    SPAWNER,
    TRIAL_SPAWNER,
    /** A chicken hatched from a thrown egg. */
    EGG,
    /** A spawn egg used by a player. */
    SPAWNER_EGG,
    BUCKET,
    LIGHTNING,
    BUILD_SNOWMAN,
    BUILD_IRONGOLEM,
    BUILD_COPPERGOLEM,
    BUILD_WITHER,
    VILLAGE_DEFENSE,
    VILLAGE_INVASION,
    BREEDING,
    /** A smaller slime or magma cube from one that died. */
    SLIME_SPLIT,
    /** A zombie calling for reinforcements. */
    REINFORCEMENTS,
    NETHER_PORTAL,
    /** A spawn egg fired from a dispenser. */
    DISPENSE_EGG,
    /** A villager turned into a zombie villager. */
    INFECTION,
    /** A zombie villager cured back into a villager. */
    CURED,
    OCELOT_BABY,
    /** A silverfish leaving an infested block. */
    SILVERFISH_BLOCK,
    MOUNT,
    /** A skeleton trap horse and its riders. */
    TRAP,
    /** An endermite from a thrown ender pearl. */
    ENDER_PEARL,
    /** A parrot leaving a player's shoulder. */
    SHOULDER_ENTITY,
    /** A zombie or husk converted by drowning. */
    DROWNED,
    /** A cow left behind by a sheared mooshroom. */
    SHEARED,
    EXPLOSION,
    RAID,
    PATROL,
    /** A bee leaving its hive or nest. */
    BEEHIVE,
    /** A piglin, piglin brute or hoglin zombified in the overworld. */
    PIGLIN_ZOMBIFIED,
    /** A vex summoned by an evoker. */
    SPELL,
    /** A skeleton turned into a stray by powder snow. */
    FROZEN,
    /** A tadpole growing into a frog. */
    METAMORPHOSIS,
    /** An allay duplicated. */
    DUPLICATION,
    COMMAND,
    ENCHANTMENT,
    /** A slime from the oozing effect. */
    POTION_EFFECT,
    REHYDRATION,
    CUSTOM,
    OMINOUS_ITEM_SPAWNER,
    /** The platform could not tell the reason. */
    DEFAULT
  }
}
