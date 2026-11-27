package art.arcane.adapt.content.adaptation.blocking;

import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import java.util.List;

public final class BlockingPreferences {
  static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("require-sneak", key("require_sneak"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> SNEAK_STANCE = CommonPreferences.toggle("sneak-stance", key("sneak_stance"));
  public static final PlayerPreference<CommonPreferences.Toggle> PLAYERS = CommonPreferences.toggle("player-targets", key("player_targets"));
  static final PlayerPreference<CommonPreferences.Toggle> PASSIVE = CommonPreferences.toggle("ignore-passive", key("ignore_passive"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> STAGGER = CommonPreferences.toggle("stagger", key("stagger"));
  static final PlayerPreference<CommonPreferences.Toggle> GROUND = CommonPreferences.toggle("ground-swap", key("ground_swap"));
  static final PlayerPreference<CommonPreferences.Toggle> FALL = CommonPreferences.toggle("fall-swap", key("fall_swap"));
  static final PlayerPreference<CommonPreferences.Toggle> RESISTANCE = CommonPreferences.toggle("resistance", key("resistance"));
  static final PlayerPreference<CommonPreferences.Toggle> RECOVERY = CommonPreferences.toggle("shield-recovery", key("shield_recovery"));
  static final PlayerPreference<CommonPreferences.Toggle> SHIELD = CommonPreferences.toggle("shield-repair", key("shield_repair"));
  static final PlayerPreference<CommonPreferences.Toggle> ARMOR = CommonPreferences.toggle("armor-repair", key("armor_repair"));
  public static final PlayerPreference<Allies> ALLIES = PlayerPreference.of("allies", Allies.class, Allies.ALL, List.of(
          choice(Allies.ALL, "all", Id.minecraft("shield")),
          choice(Allies.SAME_TEAM, "team", Id.minecraft("white_banner")))).labelled(key("allies"));
  public static final PlayerPreference<Reserve> RESERVE = PlayerPreference.of("durability-reserve", Reserve.class, Reserve.NONE, List.of(
          choice(Reserve.NONE, "none", Id.minecraft("shield")),
          choice(Reserve.QUARTER, "quarter", Id.minecraft("iron_ingot")),
          choice(Reserve.HALF, "half", Id.minecraft("diamond")))).labelled(key("reserve"));
  static final PlayerPreference<Fall> FALL_DISTANCE = PlayerPreference.of("fall-distance", Fall.class, Fall.FOUR, List.of(
          choice(Fall.FOUR, "four", Id.minecraft("feather")),
          choice(Fall.EIGHT, "eight", Id.minecraft("elytra")),
          choice(Fall.TWELVE, "twelve", Id.minecraft("phantom_membrane")))).labelled(key("fall_distance"));

  private BlockingPreferences() {}
  private static Text key(String id) {
    return Text.key("adapt.blocking.preferences." + id);
  }
  private static <E extends Enum<E>> PlayerPreference.Choice<E> choice(E value, String id, Id icon) {
    return new PlayerPreference.Choice<>(value, key(id), icon, 1);
  }
  public enum Allies {
    ALL, SAME_TEAM;
    public boolean accepts(PlayerRef owner, PlayerRef ally) {
      if (this == ALL) { return true; }
      return owner.isOnTeamWith(ally);
    }
  }
  public enum Reserve {
    NONE(0D), QUARTER(0.25D), HALF(0.5D);
    private final double fraction;
    Reserve(double fraction) { this.fraction = fraction; }
    public boolean permits(ItemRef item, int cost) {
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
