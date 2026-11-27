package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.api.content.AdaptationDamageTargets;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.List;

public final class SwordPreferences {
  private static final Id DIAMOND_SWORD = Id.minecraft("diamond_sword");
  private static final Id NETHERITE_SWORD = Id.minecraft("netherite_sword");
  static final PlayerPreference<CommonPreferences.Toggle> AIRBORNE = CommonPreferences.toggle("airborne-only", key("airborne"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("require-sneak", key("sneak"), CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<CommonPreferences.Toggle> IGNORE_PASSIVE = CommonPreferences.toggle("ignore-passive", key("ignore_passive"), CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<CommonPreferences.Toggle> VISUALS = CommonPreferences.toggle("bleed-visuals", key("visuals"));
  static final PlayerPreference<CommonPreferences.Toggle> GLOW = CommonPreferences.toggle("threat-glow", key("glow"));
  public static final PlayerPreference<Targets> TARGETS = PlayerPreference.of("targets", Targets.class, Targets.ALL, List.of(
          choice(Targets.ALL, "all", Id.minecraft("diamond_sword")),
          choice(Targets.HOSTILE, "hostile", Id.minecraft("zombie_head")),
          choice(Targets.NON_PLAYERS, "non_players", Id.minecraft("bone")))).labelled(key("targets"));
  static final PlayerPreference<Swords> SWORDS = PlayerPreference.of("swords", Swords.class, Swords.ALL, List.of(
          choice(Swords.ALL, "all_swords", Id.minecraft("iron_sword")),
          choice(Swords.DIAMOND, "diamond", Id.minecraft("diamond_sword")),
          choice(Swords.NETHERITE, "netherite", Id.minecraft("netherite_sword")),
          choice(Swords.PRECIOUS, "precious", Id.minecraft("netherite_ingot")))).labelled(key("swords"));
  static final PlayerPreference<Foliage> FOLIAGE = PlayerPreference.of("foliage", Foliage.class, Foliage.ALL, List.of(
          choice(Foliage.ALL, "all_foliage", Id.minecraft("vine")),
          choice(Foliage.LEAVES, "leaves", Id.minecraft("oak_leaves")),
          choice(Foliage.GRASS, "grass", Id.minecraft("short_grass")),
          choice(Foliage.VINES, "vines", Id.minecraft("vine")))).labelled(key("foliage"));
  static final PlayerPreference<Reserve> DURABILITY = PlayerPreference.of("durability-reserve", Reserve.class, Reserve.NONE, List.of(
          choice(Reserve.NONE, "none", Id.minecraft("wooden_sword")),
          choice(Reserve.QUARTER, "quarter", Id.minecraft("iron_sword")),
          choice(Reserve.HALF, "half", Id.minecraft("diamond_sword")))).labelled(key("durability"));
  static final PlayerPreference<XpReserve> XP = PlayerPreference.of("xp-reserve", XpReserve.class, XpReserve.NONE, List.of(
          choice(XpReserve.NONE, "no_xp", Id.minecraft("glass_bottle")),
          choice(XpReserve.FIVE, "five", Id.minecraft("experience_bottle")),
          choice(XpReserve.TEN, "ten", Id.minecraft("enchanting_table")),
          choice(XpReserve.THIRTY, "thirty", Id.minecraft("bookshelf")))).labelled(key("xp"));

  private SwordPreferences() {
  }


  private static Text key(String id) {
    return Text.key("adapt.sword.preferences." + id);
  }

  private static <E extends Enum<E>> PlayerPreference.Choice<E> choice(E value, String id, Id icon) {
    return new PlayerPreference.Choice<>(value, key(id), icon, 1);
  }

  public enum Targets {
    ALL, HOSTILE, NON_PLAYERS;

    public boolean accepts(LivingRef target) {
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
