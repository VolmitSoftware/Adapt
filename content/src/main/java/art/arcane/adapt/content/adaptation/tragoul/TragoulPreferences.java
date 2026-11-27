package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.viewer.GlowColor;
import java.util.List;
import java.util.Set;

public final class TragoulPreferences {
  static final PlayerPreference<CommonPreferences.Toggle> BLOOD = CommonPreferences.toggle("blood-globes", key("blood_globes"));
  static final PlayerPreference<CommonPreferences.Toggle> BONE = CommonPreferences.toggle("bone-globes", key("bone_globes"));
  public static final PlayerPreference<CommonPreferences.Toggle> PASSIVE = CommonPreferences.toggle("ignore-passive", key("ignore_passive"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> REPLACE = CommonPreferences.toggle("replace-oldest", key("replace_oldest"));
  public static final PlayerPreference<Targets> TARGETS = PlayerPreference.of("targets", Targets.class, Targets.ALL, List.of(
          choice(Targets.ALL, "all", Id.minecraft("diamond_sword")),
          choice(Targets.UNDEAD, "undead", Id.minecraft("zombie_head")),
          choice(Targets.ARTHROPODS, "arthropods", Id.minecraft("spider_eye")),
          choice(Targets.NON_PLAYERS, "non_players", Id.minecraft("bone")))).labelled(key("targets"));
  public static final PlayerPreference<Reserve> BONES = PlayerPreference.of("bone-reserve", Reserve.class, Reserve.NONE, List.of(
          choice(Reserve.NONE, "none", Id.minecraft("bone")),
          choice(Reserve.FOUR, "four", Id.minecraft("bone_block")),
          choice(Reserve.EIGHT, "eight", Id.minecraft("chest")))).labelled(key("bones"));
  static final PlayerPreference<Stance> STANCE = PlayerPreference.of("stance", Stance.class, Stance.ASSIST, List.of(
          choice(Stance.ASSIST, "assist", Id.minecraft("bow")),
          choice(Stance.DEFEND, "defend", Id.minecraft("shield")),
          choice(Stance.PASSIVE, "passive", Id.minecraft("bone")))).labelled(key("stance"));
  static final PlayerPreference<Palette> PALETTE = PlayerPreference.of("health-palette", Palette.class, Palette.WARM, List.of(
          choice(Palette.WARM, "warm", Id.minecraft("red_dye")),
          choice(Palette.COOL, "cool", Id.minecraft("cyan_dye")))).labelled(key("palette"));
  private TragoulPreferences() {}
  private static Text key(String id) {
    return Text.key("adapt.tragoul.preferences." + id);
  }
  private static <E extends Enum<E>> PlayerPreference.Choice<E> choice(E value, String id, Id icon) {
    return new PlayerPreference.Choice<>(value, key(id), icon, 1);
  }
  public enum Targets {
    ALL, UNDEAD, ARTHROPODS, NON_PLAYERS;
    private static final Id PLAYER = Id.minecraft("player");
    private static final Set<Id> UNDEAD_TYPES = Set.of(Id.minecraft("zombie"), Id.minecraft("zombie_villager"), Id.minecraft("husk"),
        Id.minecraft("drowned"), Id.minecraft("skeleton"), Id.minecraft("stray"), Id.minecraft("bogged"), Id.minecraft("wither_skeleton"),
        Id.minecraft("phantom"), Id.minecraft("zombified_piglin"), Id.minecraft("zoglin"), Id.minecraft("wither"),
        Id.minecraft("skeleton_horse"), Id.minecraft("zombie_horse"));
    private static final Set<Id> ARTHROPOD_TYPES = Set.of(Id.minecraft("spider"), Id.minecraft("cave_spider"), Id.minecraft("silverfish"),
        Id.minecraft("endermite"), Id.minecraft("bee"));
    boolean accepts(Id type) {
      return switch (this) {
        case ALL -> true;
        case NON_PLAYERS -> !PLAYER.equals(type);
        case UNDEAD -> UNDEAD_TYPES.contains(type);
        case ARTHROPODS -> ARTHROPOD_TYPES.contains(type);
      };
    }
  }
  public enum Reserve {
    NONE(0), FOUR(4), EIGHT(8);
    private final int remaining;
    Reserve(int remaining) { this.remaining = remaining; }
    public int required(int cost) { return cost + remaining; }
  }
  enum Stance { ASSIST, DEFEND, PASSIVE }
  enum Palette {
    WARM, COOL;
    GlowColor color(GlowColor original) {
      if (this == WARM) { return original; }
      return switch (original) {
        case DARK_RED -> GlowColor.DARK_BLUE;
        case RED -> GlowColor.BLUE;
        case GOLD -> GlowColor.AQUA;
        default -> GlowColor.WHITE;
      };
    }
  }
}
