package art.arcane.adapt.content.adaptation.unarmed;

import art.arcane.adapt.api.content.AdaptationDamageTargets;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import java.util.List;

public final class UnarmedPreferences {
  static final PlayerPreference<CommonPreferences.Toggle> PLAYERS = CommonPreferences.toggle("player-targets", key("player_targets"));
  static final PlayerPreference<CommonPreferences.Toggle> ARMOR = CommonPreferences.toggle("mob-armor", key("mob_armor"));
  public static final PlayerPreference<CommonPreferences.Toggle> COMBAT = CommonPreferences.toggle("combat", key("combat"));
  public static final PlayerPreference<CommonPreferences.Toggle> BREAKING = CommonPreferences.toggle("soft-blocks", key("soft_blocks"));
  static final PlayerPreference<CommonPreferences.Toggle> AIRBORNE = CommonPreferences.toggle("airborne-only", key("airborne_only"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> ARM = CommonPreferences.toggle("require-arming", key("require_arming"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> MANUAL = CommonPreferences.toggle("manual-meditation", key("manual_meditation"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> ARMED = CommonPreferences.toggle("meditation-armed", key("meditation_armed"), CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<Targets> TARGETS = PlayerPreference.of("targets", Targets.class, Targets.ALL, List.of(
          choice(Targets.ALL, "all", Id.minecraft("diamond_sword")),
          choice(Targets.HOSTILE, "hostile", Id.minecraft("zombie_head")),
          choice(Targets.NON_PLAYERS, "non_players", Id.minecraft("bone")))).labelled(key("targets"));
  static final PlayerPreference<Loadout> LOADOUT = PlayerPreference.of("loadout", Loadout.class, Loadout.BOTH, List.of(
          choice(Loadout.BOTH, "both", Id.minecraft("shield")),
          choice(Loadout.FISTS, "fists", Id.minecraft("leather")),
          choice(Loadout.SHIELD, "shield", Id.minecraft("shield")))).labelled(key("loadout"));
  public static final PlayerPreference<Release> RELEASE = PlayerPreference.of("release", Release.class, Release.SNEAK_OR_HIT, List.of(
          choice(Release.SNEAK_OR_HIT, "sneak_or_hit", Id.minecraft("leather_boots")),
          choice(Release.NEXT_HIT, "next_hit", Id.minecraft("leather")))).labelled(key("release"));
  static final PlayerPreference<Reserve> HUNGER = PlayerPreference.of("hunger-reserve", Reserve.class, Reserve.NONE, List.of(
          choice(Reserve.NONE, "none", Id.minecraft("bowl")),
          choice(Reserve.FIVE, "five", Id.minecraft("apple")),
          choice(Reserve.TEN, "ten", Id.minecraft("bread")))).labelled(key("hunger"));
  private UnarmedPreferences() {}
  private static Text key(String id) {
    return Text.key("adapt.unarmed.preferences." + id);
  }
  private static <E extends Enum<E>> PlayerPreference.Choice<E> choice(E value, String id, Id icon) {
    return new PlayerPreference.Choice<>(value, key(id), icon, 1);
  }
  public enum Targets {
    ALL, HOSTILE, NON_PLAYERS;
    boolean accepts(LivingRef target) {
      return this == ALL || (!(target instanceof PlayerRef) && (this == NON_PLAYERS || AdaptationDamageTargets.allows(target, true)));
    }
  }
  enum Loadout {
    BOTH, FISTS, SHIELD;
    boolean accepts(boolean fists, boolean shield) { return (this != SHIELD && fists) || (this != FISTS && shield); }
  }
  public enum Release { SNEAK_OR_HIT, NEXT_HIT }
  enum Reserve {
    NONE(0), FIVE(5), TEN(10);
    private final int remaining;
    Reserve(int remaining) { this.remaining = remaining; }
    boolean permits(int food, int cost) { return food - cost >= remaining; }
  }
}
