package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.platform.value.Id;

import java.util.Map;

/**
 * The Bukkit names discovery records and stat keys were stored under, so player data keeps matching on every
 * platform: enum-style names for materials and entity types, and the legacy enchantment and potion effect names
 * Bukkit's deprecated {@code getName()} reports.
 */
public final class DiscoveryNames {
  private static final Map<String, String> ENCHANTMENTS = Map.ofEntries(
      Map.entry("protection", "PROTECTION_ENVIRONMENTAL"),
      Map.entry("fire_protection", "PROTECTION_FIRE"),
      Map.entry("feather_falling", "PROTECTION_FALL"),
      Map.entry("blast_protection", "PROTECTION_EXPLOSIONS"),
      Map.entry("projectile_protection", "PROTECTION_PROJECTILE"),
      Map.entry("respiration", "OXYGEN"),
      Map.entry("aqua_affinity", "WATER_WORKER"),
      Map.entry("sharpness", "DAMAGE_ALL"),
      Map.entry("smite", "DAMAGE_UNDEAD"),
      Map.entry("bane_of_arthropods", "DAMAGE_ARTHROPODS"),
      Map.entry("looting", "LOOT_BONUS_MOBS"),
      Map.entry("efficiency", "DIG_SPEED"),
      Map.entry("unbreaking", "DURABILITY"),
      Map.entry("fortune", "LOOT_BONUS_BLOCKS"),
      Map.entry("power", "ARROW_DAMAGE"),
      Map.entry("punch", "ARROW_KNOCKBACK"),
      Map.entry("flame", "ARROW_FIRE"),
      Map.entry("infinity", "ARROW_INFINITE"),
      Map.entry("luck_of_the_sea", "LUCK"));
  private static final Map<String, String> EFFECTS = Map.ofEntries(
      Map.entry("speed", "SPEED"),
      Map.entry("slowness", "SLOW"),
      Map.entry("haste", "FAST_DIGGING"),
      Map.entry("mining_fatigue", "SLOW_DIGGING"),
      Map.entry("strength", "INCREASE_DAMAGE"),
      Map.entry("instant_health", "HEAL"),
      Map.entry("instant_damage", "HARM"),
      Map.entry("jump_boost", "JUMP"),
      Map.entry("nausea", "CONFUSION"),
      Map.entry("regeneration", "REGENERATION"),
      Map.entry("resistance", "DAMAGE_RESISTANCE"),
      Map.entry("fire_resistance", "FIRE_RESISTANCE"),
      Map.entry("water_breathing", "WATER_BREATHING"),
      Map.entry("invisibility", "INVISIBILITY"),
      Map.entry("blindness", "BLINDNESS"),
      Map.entry("night_vision", "NIGHT_VISION"),
      Map.entry("hunger", "HUNGER"),
      Map.entry("weakness", "WEAKNESS"),
      Map.entry("poison", "POISON"),
      Map.entry("wither", "WITHER"),
      Map.entry("health_boost", "HEALTH_BOOST"),
      Map.entry("absorption", "ABSORPTION"),
      Map.entry("saturation", "SATURATION"),
      Map.entry("glowing", "GLOWING"),
      Map.entry("levitation", "LEVITATION"),
      Map.entry("luck", "LUCK"),
      Map.entry("unluck", "UNLUCK"),
      Map.entry("slow_falling", "SLOW_FALLING"),
      Map.entry("conduit_power", "CONDUIT_POWER"),
      Map.entry("dolphins_grace", "DOLPHINS_GRACE"),
      Map.entry("bad_omen", "BAD_OMEN"),
      Map.entry("hero_of_the_village", "HERO_OF_THE_VILLAGE"),
      Map.entry("darkness", "DARKNESS"));

  private DiscoveryNames() {
  }

  /** {@code Material#name()} / {@code EntityType#name()} for vanilla ids; the full id for other namespaces. */
  public static String enumName(Id id) {
    return id.isMinecraft() ? id.legacyName() : id.toString();
  }

  /** {@code Enchantment#getName()}. */
  public static String enchantment(Id enchantment) {
    if (!enchantment.isMinecraft()) {
      return enchantment.toString();
    }
    String legacy = ENCHANTMENTS.get(enchantment.path());
    return legacy == null ? enchantment.legacyName() : legacy;
  }

  /** {@code PotionEffectType#getName()}. */
  public static String effect(Id effect) {
    String legacy = effect.isMinecraft() ? EFFECTS.get(effect.path()) : null;
    return legacy == null ? effect.toString() : legacy;
  }
}
