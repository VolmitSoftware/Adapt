package art.arcane.adapt.platform.entity;

/**
 * Classification checks that replace Bukkit entity interface checks. Use {@code instanceof} for
 * {@link PlayerRef}, {@link LivingRef}, {@link MobRef}, {@link ProjectileRef} and
 * {@link ItemEntityRef}; use traits for the rest.
 */
public enum EntityTrait {
  /** Bukkit Monster, vanilla Monster. */
  MONSTER,
  /** Bukkit Enemy, vanilla Enemy: monsters plus slimes, ghasts, phantoms, shulkers and similar. */
  ENEMY,
  ANIMAL,
  /** Can be tamed (tamed or not). */
  TAMEABLE,
  /** Bukkit AbstractHorse, vanilla AbstractHorse: horses, donkeys, mules, llamas, camels, skeleton and zombie horses. */
  HORSE,
  WATER_MOB,
  GOLEM,
  VILLAGER,
  BOSS,
  UNDEAD,
  ARMOR_STAND,
  DISPLAY,
  VEHICLE,
  EXPERIENCE_ORB,
  /** Bukkit Fireball, vanilla AbstractHurtingProjectile: fireballs, dragon fireballs, wither skulls and wind charges. */
  FIREBALL,
  /** Bukkit AbstractArrow, vanilla AbstractArrow: arrows, spectral arrows and tridents. */
  ARROW,
  /** Bukkit Fish, vanilla AbstractFish: cod, salmon, pufferfish, tropical fish and tadpoles. */
  FISH,
  /** Bukkit ItemFrame, vanilla ItemFrame: item frames and glow item frames. */
  ITEM_FRAME,
  /** Bukkit Painting, vanilla Painting. */
  PAINTING,
  /** Bukkit Ambient, vanilla AmbientCreature: bats. */
  AMBIENT,
  /** Bukkit Slime, vanilla Slime: slimes and magma cubes. */
  SLIME,
  /** Bukkit Zombie, vanilla Zombie: zombies, husks, drowned, zombie villagers and zombified piglins. */
  ZOMBIE,
  /** Bukkit AbstractSkeleton, vanilla AbstractSkeleton: skeletons, strays, wither skeletons, bogged and similar. */
  SKELETON,
  /** Bukkit Spider, vanilla Spider: spiders and cave spiders. */
  SPIDER
}
