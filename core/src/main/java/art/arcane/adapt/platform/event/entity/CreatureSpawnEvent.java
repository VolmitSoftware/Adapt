package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.value.Loc;

import java.util.Objects;

/** A living entity spawns. Mutable: cancelled. */
public class CreatureSpawnEvent extends CancellableEvent {
  private final LivingRef entity;
  private final Loc location;
  private final Reason reason;

  public CreatureSpawnEvent(LivingRef entity, Loc location, Reason reason) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.location = Objects.requireNonNull(location, "location");
    this.reason = reason == null ? Reason.DEFAULT : reason;
  }

  public LivingRef entity() {
    return entity;
  }

  public Loc location() {
    return location;
  }

  public Reason reason() {
    return reason;
  }

  public enum Reason {
    NATURAL,
    JOCKEY,
    CHUNK_GEN,
    SPAWNER,
    TRIAL_SPAWNER,
    EGG,
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
    SLIME_SPLIT,
    REINFORCEMENTS,
    NETHER_PORTAL,
    DISPENSE_EGG,
    INFECTION,
    CURED,
    OCELOT_BABY,
    SILVERFISH_BLOCK,
    MOUNT,
    TRAP,
    ENDER_PEARL,
    SHOULDER_ENTITY,
    DROWNED,
    SHEARED,
    EXPLOSION,
    RAID,
    PATROL,
    BEEHIVE,
    PIGLIN_ZOMBIFIED,
    SPELL,
    FROZEN,
    METAMORPHOSIS,
    DUPLICATION,
    COMMAND,
    ENCHANTMENT,
    POTION_EFFECT,
    REHYDRATION,
    CUSTOM,
    OMINOUS_ITEM_SPAWNER,
    DEFAULT
  }
}
