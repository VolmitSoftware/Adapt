package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.adaptation.AdaptationDamageTargets;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

public final class RangedPreferences {
  static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("require-sneak", key("sneak", "Require sneaking"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> GLOW = CommonPreferences.toggle("target-glow", key("glow", "Private target glow"));
  static final PlayerPreference<CommonPreferences.Toggle> IGNORE_PASSIVE = CommonPreferences.toggle("ignore-passive", key("ignore_passive", "Ignore passive mobs"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> DAMPEN = CommonPreferences.toggle("dampen-velocity", key("dampen", "Slow target momentum"));
  static final PlayerPreference<CommonPreferences.Toggle> IMPACT = CommonPreferences.toggle("impact-marker", key("impact", "Impact marker"));
  static final PlayerPreference<CommonPreferences.Toggle> TRAIL = CommonPreferences.toggle("trajectory-trail", key("trail", "Trajectory trail"));
  static final PlayerPreference<CommonPreferences.Toggle> ALL_PROJECTILES = CommonPreferences.toggle("all-projectiles", key("all_projectiles", "Include permitted thrown projectiles"));
  static final PlayerPreference<ShotScope> SHOTS = new PlayerPreference<>(ShotScope.class,
      new PlayerPreference.Definition<>("projectiles", key("projectiles", "Eligible projectiles"), ShotScope.ALL, List.of(
          choice(ShotScope.ALL, "all", "All permitted", Material.ARROW),
          choice(ShotScope.BOW, "bow", "Bow arrows", Material.BOW),
          choice(ShotScope.CROSSBOW, "crossbow", "Crossbow arrows", Material.CROSSBOW),
          choice(ShotScope.ARROWS, "arrows", "All arrows", Material.SPECTRAL_ARROW),
          choice(ShotScope.TRIDENTS, "tridents", "Tridents", Material.TRIDENT),
          choice(ShotScope.THROWN, "thrown", "Thrown projectiles", Material.SNOWBALL))));
  static final PlayerPreference<Targets> TARGETS = new PlayerPreference<>(Targets.class,
      new PlayerPreference.Definition<>("targets", key("targets", "Eligible targets"), Targets.ALL, List.of(
          choice(Targets.ALL, "all", "All permitted", Material.TARGET),
          choice(Targets.HOSTILE, "hostile", "Hostile mobs only", Material.ZOMBIE_HEAD),
          choice(Targets.NON_PLAYERS, "non_players", "Exclude players", Material.PLAYER_HEAD))));
  static final PlayerPreference<Items> ITEMS = new PlayerPreference<>(Items.class,
      new PlayerPreference.Definition<>("items", key("items", "Collected items"), Items.ALL, List.of(
          choice(Items.ALL, "all", "All permitted", Material.CHEST),
          choice(Items.BLOCKS, "blocks", "Building blocks", Material.STONE),
          choice(Items.VALUABLES, "valuables", "Ores and valuables", Material.DIAMOND),
          choice(Items.FOOD, "food", "Food", Material.APPLE))));
  static final PlayerPreference<Preview> PREVIEW = new PlayerPreference<>(Preview.class,
      new PlayerPreference.Definition<>("preview-trigger", key("preview", "Preview while"), Preview.BOTH, List.of(
          choice(Preview.BOTH, "both", "Drawing or sneaking", Material.BOW),
          choice(Preview.DRAWING, "drawing", "Drawing only", Material.ARROW),
          choice(Preview.SNEAKING, "sneaking", "Sneaking only", Material.LEATHER_BOOTS))));

  private RangedPreferences() {
  }

  public static void addMessages(MessageCatalog.Builder builder) {
    Set<String> added = new HashSet<>();
    for (PlayerPreference<?> preference : List.of(SNEAK, GLOW, IGNORE_PASSIVE, DAMPEN, IMPACT, TRAIL, ALL_PROJECTILES, SHOTS, TARGETS, ITEMS, PREVIEW)) {
      if (added.add(preference.label().id())) {
        builder.add(preference.label());
      }
      for (PlayerPreference.Choice<?> choice : preference.choices()) {
        if (choice.label().id().startsWith("ranged.preferences.") && added.add(choice.label().id())) {
          builder.add(choice.label());
        }
      }
    }
  }

  private static TextKey key(String id, String text) {
    return TextKey.of("ranged.preferences." + id, text);
  }

  private static <E extends Enum<E>> PlayerPreference.Choice<E> choice(E value, String id, String label, Material icon) {
    return new PlayerPreference.Choice<>(value, key(id, label), icon, 1);
  }

  enum ShotScope {
    ALL, BOW, CROSSBOW, ARROWS, TRIDENTS, THROWN;

    boolean accepts(Material weapon) {
      return switch (this) {
        case ALL -> true;
        case BOW -> weapon == Material.BOW;
        case CROSSBOW -> weapon == Material.CROSSBOW;
        case ARROWS -> weapon == Material.BOW || weapon == Material.CROSSBOW;
        case TRIDENTS -> weapon == Material.TRIDENT;
        case THROWN -> weapon != Material.BOW && weapon != Material.CROSSBOW && weapon != Material.TRIDENT;
      };
    }

    boolean accepts(Projectile projectile) {
      return switch (this) {
        case ALL -> true;
        case BOW -> projectile instanceof AbstractArrow arrow && !(arrow instanceof Trident) && !arrow.isShotFromCrossbow();
        case CROSSBOW -> projectile instanceof AbstractArrow arrow && arrow.isShotFromCrossbow();
        case ARROWS -> projectile instanceof AbstractArrow && !(projectile instanceof Trident);
        case TRIDENTS -> projectile instanceof Trident;
        case THROWN -> !(projectile instanceof AbstractArrow);
      };
    }
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

  enum Items {
    ALL, BLOCKS, VALUABLES, FOOD;

    boolean accepts(Material material) {
      return switch (this) {
        case ALL -> true;
        case BLOCKS -> material.isBlock();
        case FOOD -> material.isEdible();
        case VALUABLES -> material.name().endsWith("_ORE") || material.name().endsWith("_INGOT")
            || material.name().startsWith("RAW_") || material == Material.DIAMOND || material == Material.EMERALD
            || material == Material.COAL || material == Material.REDSTONE || material == Material.LAPIS_LAZULI;
      };
    }
  }

  enum Preview {
    BOTH, DRAWING, SNEAKING
  }
}
