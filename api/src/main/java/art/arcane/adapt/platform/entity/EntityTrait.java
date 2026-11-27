package art.arcane.adapt.platform.entity;

import org.jetbrains.annotations.ApiStatus;

/**
 * Classification checks that replace Bukkit entity interface checks. Use {@code instanceof} for
 * {@link PlayerRef}, {@link LivingRef}, {@link MobRef}, {@link ProjectileRef} and
 * {@link ItemEntityRef}; use traits for the rest.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum EntityTrait {
  /** Bukkit Monster, vanilla Monster. */
  MONSTER,
  /** Bukkit Enemy, vanilla Enemy: monsters plus slimes, ghasts, phantoms, shulkers and similar. */
  ENEMY,
  /** Bukkit Animals, vanilla Animal: breedable land and water animals. */
  ANIMAL,
  /** Can be tamed (tamed or not). */
  TAMEABLE,
  /** Bukkit AbstractHorse, vanilla AbstractHorse: horses, donkeys, mules, llamas, camels, skeleton and zombie horses. */
  HORSE,
  /** Bukkit WaterMob, vanilla WaterAnimal: squid, dolphins, fish and similar. */
  WATER_MOB,
  /** Bukkit Golem, vanilla AbstractGolem: iron golems, snow golems, shulkers and similar. */
  GOLEM,
  /** Villagers and wandering traders. */
  VILLAGER,
  /** The ender dragon and the wither. */
  BOSS,
  /** Entity types in the {@code minecraft:undead} tag. */
  UNDEAD,
  ARMOR_STAND,
  /** Block, item and text display entities. */
  DISPLAY,
  /** Rideable entities: boats, minecarts, horses and their kin, pigs and striders. */
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
