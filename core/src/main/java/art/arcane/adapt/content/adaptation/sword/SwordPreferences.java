package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.api.adaptation.AdaptationDamageTargets;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

public final class SwordPreferences {
  private static final Id DIAMOND_SWORD = Id.minecraft("diamond_sword");
  private static final Id NETHERITE_SWORD = Id.minecraft("netherite_sword");
  static final PlayerPreference<CommonPreferences.Toggle> AIRBORNE = CommonPreferences.toggle("airborne-only", key("airborne", "Require an airborne sprint attack"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("require-sneak", key("sneak", "Require sneaking"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> IGNORE_PASSIVE = CommonPreferences.toggle("ignore-passive", key("ignore_passive", "Ignore passive mobs"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> VISUALS = CommonPreferences.toggle("bleed-visuals", key("visuals", "Bleed particles"));
  static final PlayerPreference<CommonPreferences.Toggle> GLOW = CommonPreferences.toggle("threat-glow", key("glow", "Mark focused attacker"));
  static final PlayerPreference<Targets> TARGETS = new PlayerPreference<>(Targets.class,
      new PlayerPreference.Definition<>("targets", key("targets", "Eligible targets"), Targets.ALL, List.of(
          choice(Targets.ALL, "all", "All permitted", Id.minecraft("diamond_sword")),
          choice(Targets.HOSTILE, "hostile", "Hostile mobs", Id.minecraft("zombie_head")),
          choice(Targets.NON_PLAYERS, "non_players", "Non-player targets", Id.minecraft("bone")))));
  static final PlayerPreference<Swords> SWORDS = new PlayerPreference<>(Swords.class,
      new PlayerPreference.Definition<>("swords", key("swords", "Eligible heirloom swords"), Swords.ALL, List.of(
          choice(Swords.ALL, "all_swords", "All swords", Id.minecraft("iron_sword")),
          choice(Swords.DIAMOND, "diamond", "Diamond only", Id.minecraft("diamond_sword")),
          choice(Swords.NETHERITE, "netherite", "Netherite only", Id.minecraft("netherite_sword")),
          choice(Swords.PRECIOUS, "precious", "Diamond and netherite", Id.minecraft("netherite_ingot")))));
  static final PlayerPreference<Foliage> FOLIAGE = new PlayerPreference<>(Foliage.class,
      new PlayerPreference.Definition<>("foliage", key("foliage", "Foliage to cut"), Foliage.ALL, List.of(
          choice(Foliage.ALL, "all_foliage", "All permitted foliage", Id.minecraft("vine")),
          choice(Foliage.LEAVES, "leaves", "Leaves only", Id.minecraft("oak_leaves")),
          choice(Foliage.GRASS, "grass", "Grass and ferns", Id.minecraft("short_grass")),
          choice(Foliage.VINES, "vines", "Vines only", Id.minecraft("vine")))));
  static final PlayerPreference<Reserve> DURABILITY = new PlayerPreference<>(Reserve.class,
      new PlayerPreference.Definition<>("durability-reserve", key("durability", "Durability remaining after ritual"), Reserve.NONE, List.of(
          choice(Reserve.NONE, "none", "No extra reserve", Id.minecraft("wooden_sword")),
          choice(Reserve.QUARTER, "quarter", "Keep 25%", Id.minecraft("iron_sword")),
          choice(Reserve.HALF, "half", "Keep 50%", Id.minecraft("diamond_sword")))));
  static final PlayerPreference<XpReserve> XP = new PlayerPreference<>(XpReserve.class,
      new PlayerPreference.Definition<>("xp-reserve", key("xp", "Experience levels remaining"), XpReserve.NONE, List.of(
          choice(XpReserve.NONE, "no_xp", "No extra reserve", Id.minecraft("glass_bottle")),
          choice(XpReserve.FIVE, "five", "Keep 5 levels", Id.minecraft("experience_bottle")),
          choice(XpReserve.TEN, "ten", "Keep 10 levels", Id.minecraft("enchanting_table")),
          choice(XpReserve.THIRTY, "thirty", "Keep 30 levels", Id.minecraft("bookshelf")))));

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

  private static <E extends Enum<E>> PlayerPreference.Choice<E> choice(E value, String id, String label, Id icon) {
    return new PlayerPreference.Choice<>(value, key(id, label), icon, 1);
  }

  enum Targets {
    ALL, HOSTILE, NON_PLAYERS;

    boolean accepts(LivingRef target) {
      return accepts(target instanceof PlayerRef, !(target instanceof PlayerRef) && AdaptationDamageTargets.allows(target, true));
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
    boolean accepts(Id material) {
      return switch (this) {
        case ALL -> true;
        case DIAMOND -> DIAMOND_SWORD.equals(material);
        case NETHERITE -> NETHERITE_SWORD.equals(material);
        case PRECIOUS -> DIAMOND_SWORD.equals(material) || NETHERITE_SWORD.equals(material);
      };
    }
  }
  enum Foliage {
    ALL, LEAVES, GRASS, VINES;
    boolean accepts(Id material) {
      String name = material.isMinecraft() ? material.path() : "";
      return switch (this) {
        case ALL -> true;
        case LEAVES -> name.endsWith("_leaves");
        case GRASS -> name.equals("short_grass") || name.equals("tall_grass") || name.equals("fern") || name.equals("large_fern");
        case VINES -> name.contains("vine");
      };
    }
  }
  enum Reserve {
    NONE(0D), QUARTER(0.25D), HALF(0.5D);
    private final double fraction;
    Reserve(double fraction) { this.fraction = fraction; }
    boolean permits(ItemRef item, int cost) {
      if (this == NONE) { return true; }
      if (item == null || item.isEmpty()) { return false; }
      int maximum = item.maxDamage();
      return maximum > 0 && maximum - item.damage() - cost >= Math.ceil(maximum * fraction);
    }
  }
  enum XpReserve {
    NONE(0), FIVE(5), TEN(10), THIRTY(30);
    private final int minimum;
    XpReserve(int minimum) { this.minimum = minimum; }
    boolean permits(int levels, int cost) { return levels - cost >= minimum; }
  }
}
