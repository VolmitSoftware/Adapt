package art.arcane.adapt.content.adaptation.blocking;

import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BlockingPreferences {
  static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("require-sneak", key("require_sneak", "Require sneaking"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> SNEAK_STANCE = CommonPreferences.toggle("sneak-stance", key("sneak_stance", "Require sneak while blocking"));
  static final PlayerPreference<CommonPreferences.Toggle> PLAYERS = CommonPreferences.toggle("player-targets", key("player_targets", "Affect players"));
  static final PlayerPreference<CommonPreferences.Toggle> PASSIVE = CommonPreferences.toggle("ignore-passive", key("ignore_passive", "Ignore passive mobs"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> STAGGER = CommonPreferences.toggle("stagger", key("stagger", "Retaliatory stagger"));
  static final PlayerPreference<CommonPreferences.Toggle> GROUND = CommonPreferences.toggle("ground-swap", key("ground_swap", "Swap to chestplate on ground"));
  static final PlayerPreference<CommonPreferences.Toggle> FALL = CommonPreferences.toggle("fall-swap", key("fall_swap", "Swap to elytra while falling"));
  static final PlayerPreference<CommonPreferences.Toggle> RESISTANCE = CommonPreferences.toggle("resistance", key("resistance", "Resistance after shield disable"));
  static final PlayerPreference<CommonPreferences.Toggle> RECOVERY = CommonPreferences.toggle("shield-recovery", key("shield_recovery", "Faster shield recovery"));
  static final PlayerPreference<CommonPreferences.Toggle> SHIELD = CommonPreferences.toggle("shield-repair", key("shield_repair", "Repair shield"));
  static final PlayerPreference<CommonPreferences.Toggle> ARMOR = CommonPreferences.toggle("armor-repair", key("armor_repair", "Repair armor"));
  static final PlayerPreference<Allies> ALLIES = new PlayerPreference<>(Allies.class,
      new PlayerPreference.Definition<>("allies", key("allies", "Protected allies"), Allies.ALL, List.of(
          choice(Allies.ALL, "all", "All permitted nearby players", Id.minecraft("shield")),
          choice(Allies.SAME_TEAM, "team", "Same scoreboard team", Id.minecraft("white_banner")))));
  static final PlayerPreference<Reserve> RESERVE = new PlayerPreference<>(Reserve.class,
      new PlayerPreference.Definition<>("durability-reserve", key("reserve", "Shield durability remaining"), Reserve.NONE, List.of(
          choice(Reserve.NONE, "none", "No extra reserve", Id.minecraft("shield")),
          choice(Reserve.QUARTER, "quarter", "Keep 25%", Id.minecraft("iron_ingot")),
          choice(Reserve.HALF, "half", "Keep 50%", Id.minecraft("diamond")))));
  static final PlayerPreference<Fall> FALL_DISTANCE = new PlayerPreference<>(Fall.class,
      new PlayerPreference.Definition<>("fall-distance", key("fall_distance", "Fall distance before elytra swap"), Fall.FOUR, List.of(
          choice(Fall.FOUR, "four", "4 blocks", Id.minecraft("feather")),
          choice(Fall.EIGHT, "eight", "8 blocks", Id.minecraft("elytra")),
          choice(Fall.TWELVE, "twelve", "12 blocks", Id.minecraft("phantom_membrane")))));

  private BlockingPreferences() {}
  public static void addMessages(MessageCatalog.Builder builder) {
    Set<String> added = new HashSet<>();
    for (PlayerPreference<?> preference : List.of(SNEAK, SNEAK_STANCE, PLAYERS, PASSIVE, STAGGER, GROUND, FALL, RESISTANCE, RECOVERY, SHIELD, ARMOR, ALLIES, RESERVE, FALL_DISTANCE)) {
      if (added.add(preference.label().id())) { builder.add(preference.label()); }
      for (PlayerPreference.Choice<?> choice : preference.choices()) {
        if (choice.label().id().startsWith("blocking.preferences.") && added.add(choice.label().id())) { builder.add(choice.label()); }
      }
    }
  }
  private static TextKey key(String id, String text) { return TextKey.of("blocking.preferences." + id, text); }
  private static <E extends Enum<E>> PlayerPreference.Choice<E> choice(E value, String id, String text, Id icon) {
    return new PlayerPreference.Choice<>(value, key(id, text), icon, 1);
  }
  enum Allies {
    ALL, SAME_TEAM;
    boolean accepts(PlayerRef owner, PlayerRef ally) {
      if (this == ALL) { return true; }
      return owner.isOnTeamWith(ally);
    }
  }
  enum Reserve {
    NONE(0D), QUARTER(0.25D), HALF(0.5D);
    private final double fraction;
    Reserve(double fraction) { this.fraction = fraction; }
    boolean permits(ItemRef item, int cost) {
      if (this == NONE) { return true; }
      if (item.isEmpty()) { return false; }
      int maximum = item.maxDamage();
      return maximum > 0 && maximum - item.damage() - cost >= Math.ceil(maximum * fraction);
    }
  }
  enum Fall {
    FOUR(4), EIGHT(8), TWELVE(12);
    private final int distance;
    Fall(int distance) { this.distance = distance; }
    boolean exceeded(float fallen) { return fallen > distance; }
  }
}
