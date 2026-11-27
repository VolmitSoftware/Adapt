package art.arcane.adapt.platform.event.entity;

import org.jetbrains.annotations.ApiStatus;

/**
 * Coarse damage cause with Bukkit's names. The exact vanilla damage type is on the event as an id, see
 * {@link EntityDamageEvent#damageType()}. Causes the platform cannot map arrive as {@link #CUSTOM}.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum DamageCause {
  KILL,
  WORLD_BORDER,
  CONTACT,
  ENTITY_ATTACK,
  /** Sweep damage to entities next to the one a sword hit. */
  ENTITY_SWEEP_ATTACK,
  PROJECTILE,
  SUFFOCATION,
  FALL,
  FIRE,
  FIRE_TICK,
  /** A snow golem melting in a warm biome. */
  MELTING,
  LAVA,
  DROWNING,
  BLOCK_EXPLOSION,
  ENTITY_EXPLOSION,
  VOID,
  LIGHTNING,
  SUICIDE,
  STARVATION,
  POISON,
  MAGIC,
  WITHER,
  FALLING_BLOCK,
  THORNS,
  DRAGON_BREATH,
  FLY_INTO_WALL,
  HOT_FLOOR,
  CAMPFIRE,
  CRAMMING,
  /** A water animal drying out on land. */
  DRYOUT,
  FREEZE,
  SONIC_BOOM,
  /** Any damage the platform cannot map to another cause. */
  CUSTOM
}
