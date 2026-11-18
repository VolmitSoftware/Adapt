package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.adapt.platform.viewer.GlowColor;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TragoulPreferences {
  static final PlayerPreference<CommonPreferences.Toggle> BLOOD = CommonPreferences.toggle("blood-globes", key("blood_globes", "Collect blood globes"));
  static final PlayerPreference<CommonPreferences.Toggle> BONE = CommonPreferences.toggle("bone-globes", key("bone_globes", "Collect bone globes"));
  static final PlayerPreference<CommonPreferences.Toggle> PASSIVE = CommonPreferences.toggle("ignore-passive", key("ignore_passive", "Ignore passive mobs"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> REPLACE = CommonPreferences.toggle("replace-oldest", key("replace_oldest", "Replace oldest servant at cap"));
  static final PlayerPreference<Targets> TARGETS = new PlayerPreference<>(Targets.class,
      new PlayerPreference.Definition<>("targets", key("targets", "Eligible target types"), Targets.ALL, List.of(
          choice(Targets.ALL, "all", "All permitted targets", Id.minecraft("diamond_sword")),
          choice(Targets.UNDEAD, "undead", "Undead only", Id.minecraft("zombie_head")),
          choice(Targets.ARTHROPODS, "arthropods", "Arthropods only", Id.minecraft("spider_eye")),
          choice(Targets.NON_PLAYERS, "non_players", "Non-player targets", Id.minecraft("bone")))));
  static final PlayerPreference<Reserve> BONES = new PlayerPreference<>(Reserve.class,
      new PlayerPreference.Definition<>("bone-reserve", key("bones", "Bones remaining after use"), Reserve.NONE, List.of(
          choice(Reserve.NONE, "none", "No extra reserve", Id.minecraft("bone")),
          choice(Reserve.FOUR, "four", "Keep 4 bones", Id.minecraft("bone_block")),
          choice(Reserve.EIGHT, "eight", "Keep 8 bones", Id.minecraft("chest")))));
  static final PlayerPreference<Stance> STANCE = new PlayerPreference<>(Stance.class,
      new PlayerPreference.Definition<>("stance", key("stance", "Servant behavior"), Stance.ASSIST, List.of(
          choice(Stance.ASSIST, "assist", "Assist and hunt", Id.minecraft("bow")),
          choice(Stance.DEFEND, "defend", "Defend the owner", Id.minecraft("shield")),
          choice(Stance.PASSIVE, "passive", "Passive", Id.minecraft("bone")))));
  static final PlayerPreference<Palette> PALETTE = new PlayerPreference<>(Palette.class,
      new PlayerPreference.Definition<>("health-palette", key("palette", "Health outline palette"), Palette.WARM, List.of(
          choice(Palette.WARM, "warm", "Red to yellow", Id.minecraft("red_dye")),
          choice(Palette.COOL, "cool", "Blue to white", Id.minecraft("cyan_dye")))));
  private TragoulPreferences() {}
  public static void addMessages(MessageCatalog.Builder builder) {
    Set<String> added = new HashSet<>();
    for (PlayerPreference<?> preference : List.of(BLOOD, BONE, PASSIVE, REPLACE, TARGETS, BONES, STANCE, PALETTE)) {
      if (added.add(preference.label().id())) { builder.add(preference.label()); }
      for (PlayerPreference.Choice<?> choice : preference.choices()) {
        if (choice.label().id().startsWith("tragoul.preferences.") && added.add(choice.label().id())) { builder.add(choice.label()); }
      }
    }
  }
  private static TextKey key(String id, String text) { return TextKey.of("tragoul.preferences." + id, text); }
  private static <E extends Enum<E>> PlayerPreference.Choice<E> choice(E value, String id, String text, Id icon) {
    return new PlayerPreference.Choice<>(value, key(id, text), icon, 1);
  }
  enum Targets {
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
  enum Reserve {
    NONE(0), FOUR(4), EIGHT(8);
    private final int remaining;
    Reserve(int remaining) { this.remaining = remaining; }
    int required(int cost) { return cost + remaining; }
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
