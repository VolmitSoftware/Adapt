package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.adaptation.AdaptationDamageTargets;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

public final class SwordPreferences {
  static final PlayerPreference<CommonPreferences.Toggle> AIRBORNE = CommonPreferences.toggle("airborne-only", key("airborne", "Require an airborne sprint attack"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("require-sneak", key("sneak", "Require sneaking"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> IGNORE_PASSIVE = CommonPreferences.toggle("ignore-passive", key("ignore_passive", "Ignore passive mobs"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> VISUALS = CommonPreferences.toggle("bleed-visuals", key("visuals", "Bleed particles"));
  static final PlayerPreference<CommonPreferences.Toggle> GLOW = CommonPreferences.toggle("threat-glow", key("glow", "Mark focused attacker"));
  static final PlayerPreference<Targets> TARGETS = new PlayerPreference<>(Targets.class,
      new PlayerPreference.Definition<>("targets", key("targets", "Eligible targets"), Targets.ALL, List.of(
          choice(Targets.ALL, "all", "All permitted", Material.DIAMOND_SWORD),
          choice(Targets.HOSTILE, "hostile", "Hostile mobs", Material.ZOMBIE_HEAD),
          choice(Targets.NON_PLAYERS, "non_players", "Non-player targets", Material.BONE))));
  static final PlayerPreference<Swords> SWORDS = new PlayerPreference<>(Swords.class,
      new PlayerPreference.Definition<>("swords", key("swords", "Eligible heirloom swords"), Swords.ALL, List.of(
          choice(Swords.ALL, "all_swords", "All swords", Material.IRON_SWORD),
          choice(Swords.DIAMOND, "diamond", "Diamond only", Material.DIAMOND_SWORD),
          choice(Swords.NETHERITE, "netherite", "Netherite only", Material.NETHERITE_SWORD),
          choice(Swords.PRECIOUS, "precious", "Diamond and netherite", Material.NETHERITE_INGOT))));
  static final PlayerPreference<Foliage> FOLIAGE = new PlayerPreference<>(Foliage.class,
      new PlayerPreference.Definition<>("foliage", key("foliage", "Foliage to cut"), Foliage.ALL, List.of(
          choice(Foliage.ALL, "all_foliage", "All permitted foliage", Material.VINE),
          choice(Foliage.LEAVES, "leaves", "Leaves only", Material.OAK_LEAVES),
          choice(Foliage.GRASS, "grass", "Grass and ferns", Material.SHORT_GRASS),
          choice(Foliage.VINES, "vines", "Vines only", Material.VINE))));
  static final PlayerPreference<Reserve> DURABILITY = new PlayerPreference<>(Reserve.class,
      new PlayerPreference.Definition<>("durability-reserve", key("durability", "Durability remaining after ritual"), Reserve.NONE, List.of(
          choice(Reserve.NONE, "none", "No extra reserve", Material.WOODEN_SWORD),
          choice(Reserve.QUARTER, "quarter", "Keep 25%", Material.IRON_SWORD),
          choice(Reserve.HALF, "half", "Keep 50%", Material.DIAMOND_SWORD))));
  static final PlayerPreference<XpReserve> XP = new PlayerPreference<>(XpReserve.class,
      new PlayerPreference.Definition<>("xp-reserve", key("xp", "Experience levels remaining"), XpReserve.NONE, List.of(
          choice(XpReserve.NONE, "no_xp", "No extra reserve", Material.GLASS_BOTTLE),
          choice(XpReserve.FIVE, "five", "Keep 5 levels", Material.EXPERIENCE_BOTTLE),
          choice(XpReserve.TEN, "ten", "Keep 10 levels", Material.ENCHANTING_TABLE),
          choice(XpReserve.THIRTY, "thirty", "Keep 30 levels", Material.BOOKSHELF))));

  private SwordPreferences() {
  }

  public static void addMessages(MessageCatalog.Builder builder) {
    Set<String> added = new HashSet<>();
    for (PlayerPreference<?> preference : List.of(AIRBORNE, SNEAK, IGNORE_PASSIVE, VISUALS, GLOW, TARGETS, SWORDS, FOLIAGE, DURABILITY, XP)) {
      if (added.add(preference.label().id())) {
        builder.add(preference.label());
      }
      for (PlayerPreference.Choice<?> choice : preference.choices()) {
        if (choice.label().id().startsWith("sword.preferences.") && added.add(choice.label().id())) {
          builder.add(choice.label());
        }
      }
    }
  }

  private static TextKey key(String id, String text) {
    return TextKey.of("sword.preferences." + id, text);
  }

  private static <E extends Enum<E>> PlayerPreference.Choice<E> choice(E value, String id, String label, Material icon) {
    return new PlayerPreference.Choice<>(value, key(id, label), icon, 1);
  }

  enum Targets {
    ALL, HOSTILE, NON_PLAYERS;

    boolean accepts(LivingEntity target) {
      return accepts(target instanceof Player, !(target instanceof Player) && AdaptationDamageTargets.allows(target, true));
    }

    boolean accepts(boolean player, boolean hostile) {
      return switch (this) {
        case ALL -> true;
        case HOSTILE -> hostile;
        case NON_PLAYERS -> !player;
      };
    }
  }

  enum Swords {
    ALL, DIAMOND, NETHERITE, PRECIOUS;
    boolean accepts(Material material) {
      return switch (this) {
        case ALL -> true;
        case DIAMOND -> material == Material.DIAMOND_SWORD;
        case NETHERITE -> material == Material.NETHERITE_SWORD;
        case PRECIOUS -> material == Material.DIAMOND_SWORD || material == Material.NETHERITE_SWORD;
      };
    }
  }
  enum Foliage {
    ALL, LEAVES, GRASS, VINES;
    boolean accepts(Material material) {
      return switch (this) {
        case ALL -> true;
        case LEAVES -> material.name().endsWith("_LEAVES");
        case GRASS -> material == Material.SHORT_GRASS || material == Material.TALL_GRASS || material == Material.FERN || material == Material.LARGE_FERN;
        case VINES -> material.name().contains("VINE");
      };
    }
  }
  enum Reserve {
    NONE(0D), QUARTER(0.25D), HALF(0.5D);
    private final double fraction;
    Reserve(double fraction) { this.fraction = fraction; }
    boolean permits(ItemStack item, int cost) {
      if (this == NONE) { return true; }
      if (!(item.getItemMeta() instanceof Damageable damage)) { return false; }
      int maximum = damage.hasMaxDamage() ? damage.getMaxDamage() : item.getType().getMaxDurability();
      return maximum > 0 && maximum - damage.getDamage() - cost >= Math.ceil(maximum * fraction);
    }
  }
  enum XpReserve {
    NONE(0), FIVE(5), TEN(10), THIRTY(30);
    private final int minimum;
    XpReserve(int minimum) { this.minimum = minimum; }
    boolean permits(int levels, int cost) { return levels - cost >= minimum; }
  }
}
