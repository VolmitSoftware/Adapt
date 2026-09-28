package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Material;
import org.bukkit.ChatColor;
import org.bukkit.entity.EntityType;
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
          choice(Targets.ALL, "all", "All permitted targets", Material.DIAMOND_SWORD),
          choice(Targets.UNDEAD, "undead", "Undead only", Material.ZOMBIE_HEAD),
          choice(Targets.ARTHROPODS, "arthropods", "Arthropods only", Material.SPIDER_EYE),
          choice(Targets.NON_PLAYERS, "non_players", "Non-player targets", Material.BONE))));
  static final PlayerPreference<Reserve> BONES = new PlayerPreference<>(Reserve.class,
      new PlayerPreference.Definition<>("bone-reserve", key("bones", "Bones remaining after use"), Reserve.NONE, List.of(
          choice(Reserve.NONE, "none", "No extra reserve", Material.BONE),
          choice(Reserve.FOUR, "four", "Keep 4 bones", Material.BONE_BLOCK),
          choice(Reserve.EIGHT, "eight", "Keep 8 bones", Material.CHEST))));
  static final PlayerPreference<Stance> STANCE = new PlayerPreference<>(Stance.class,
      new PlayerPreference.Definition<>("stance", key("stance", "Servant behavior"), Stance.ASSIST, List.of(
          choice(Stance.ASSIST, "assist", "Assist and hunt", Material.BOW),
          choice(Stance.DEFEND, "defend", "Defend the owner", Material.SHIELD),
          choice(Stance.PASSIVE, "passive", "Passive", Material.BONE))));
  static final PlayerPreference<Palette> PALETTE = new PlayerPreference<>(Palette.class,
      new PlayerPreference.Definition<>("health-palette", key("palette", "Health outline palette"), Palette.WARM, List.of(
          choice(Palette.WARM, "warm", "Red to yellow", Material.RED_DYE),
          choice(Palette.COOL, "cool", "Blue to white", Material.CYAN_DYE))));
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
  private static <E extends Enum<E>> PlayerPreference.Choice<E> choice(E value, String id, String text, Material icon) {
    return new PlayerPreference.Choice<>(value, key(id, text), icon, 1);
  }
  enum Targets {
    ALL, UNDEAD, ARTHROPODS, NON_PLAYERS;
    boolean accepts(EntityType type) {
      return switch (this) {
        case ALL -> true;
        case NON_PLAYERS -> type != EntityType.PLAYER;
        case UNDEAD -> switch (type) {
          case ZOMBIE, ZOMBIE_VILLAGER, HUSK, DROWNED, SKELETON, STRAY, BOGGED, WITHER_SKELETON, PHANTOM, ZOMBIFIED_PIGLIN, ZOGLIN, WITHER, SKELETON_HORSE, ZOMBIE_HORSE -> true;
          default -> false;
        };
        case ARTHROPODS -> type == EntityType.SPIDER || type == EntityType.CAVE_SPIDER || type == EntityType.SILVERFISH || type == EntityType.ENDERMITE || type == EntityType.BEE;
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
    ChatColor color(ChatColor original) {
      if (this == WARM) { return original; }
      return switch (original) {
        case DARK_RED -> ChatColor.DARK_BLUE;
        case RED -> ChatColor.BLUE;
        case GOLD -> ChatColor.AQUA;
        default -> ChatColor.WHITE;
      };
    }
  }
}
