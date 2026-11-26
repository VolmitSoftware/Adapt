package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationDamageTargets;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.value.Id;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

public final class RangedPreferences {
  static final Id BOW = Id.minecraft("bow");
  static final Id CROSSBOW = Id.minecraft("crossbow");
  static final Id TRIDENT = Id.minecraft("trident");

  static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("require-sneak", key("sneak", "Require sneaking"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> GLOW = CommonPreferences.toggle("target-glow", key("glow", "Private target glow"));
  static final PlayerPreference<CommonPreferences.Toggle> IGNORE_PASSIVE = CommonPreferences.toggle("ignore-passive", key("ignore_passive", "Ignore passive mobs"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> DAMPEN = CommonPreferences.toggle("dampen-velocity", key("dampen", "Slow target momentum"));
  public static final PlayerPreference<CommonPreferences.Toggle> IMPACT = CommonPreferences.toggle("impact-marker", key("impact", "Impact marker"));
  public static final PlayerPreference<CommonPreferences.Toggle> TRAIL = CommonPreferences.toggle("trajectory-trail", key("trail", "Trajectory trail"));
  static final PlayerPreference<CommonPreferences.Toggle> ALL_PROJECTILES = CommonPreferences.toggle("all-projectiles", key("all_projectiles", "Include permitted thrown projectiles"));
  public static final PlayerPreference<ShotScope> SHOTS = new PlayerPreference<>(ShotScope.class,
      new PlayerPreference.Definition<>("projectiles", key("projectiles", "Eligible projectiles"), ShotScope.ALL, List.of(
          choice(ShotScope.ALL, "all", "All permitted", Id.minecraft("arrow")),
          choice(ShotScope.BOW, "bow", "Bow arrows", Id.minecraft("bow")),
          choice(ShotScope.CROSSBOW, "crossbow", "Crossbow arrows", Id.minecraft("crossbow")),
          choice(ShotScope.ARROWS, "arrows", "All arrows", Id.minecraft("spectral_arrow")),
          choice(ShotScope.TRIDENTS, "tridents", "Tridents", Id.minecraft("trident")),
          choice(ShotScope.THROWN, "thrown", "Thrown projectiles", Id.minecraft("snowball")))));
  static final PlayerPreference<Targets> TARGETS = new PlayerPreference<>(Targets.class,
      new PlayerPreference.Definition<>("targets", key("targets", "Eligible targets"), Targets.ALL, List.of(
          choice(Targets.ALL, "all", "All permitted", Id.minecraft("target")),
          choice(Targets.HOSTILE, "hostile", "Hostile mobs only", Id.minecraft("zombie_head")),
          choice(Targets.NON_PLAYERS, "non_players", "Exclude players", Id.minecraft("player_head")))));
  static final PlayerPreference<Items> ITEMS = new PlayerPreference<>(Items.class,
      new PlayerPreference.Definition<>("items", key("items", "Collected items"), Items.ALL, List.of(
          choice(Items.ALL, "all", "All permitted", Id.minecraft("chest")),
          choice(Items.BLOCKS, "blocks", "Building blocks", Id.minecraft("stone")),
          choice(Items.VALUABLES, "valuables", "Ores and valuables", Id.minecraft("diamond")),
          choice(Items.FOOD, "food", "Food", Id.minecraft("apple")))));
  static final PlayerPreference<Preview> PREVIEW = new PlayerPreference<>(Preview.class,
      new PlayerPreference.Definition<>("preview-trigger", key("preview", "Preview while"), Preview.BOTH, List.of(
          choice(Preview.BOTH, "both", "Drawing or sneaking", Id.minecraft("bow")),
          choice(Preview.DRAWING, "drawing", "Drawing only", Id.minecraft("arrow")),
          choice(Preview.SNEAKING, "sneaking", "Sneaking only", Id.minecraft("leather_boots")))));

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

  private static <E extends Enum<E>> PlayerPreference.Choice<E> choice(E value, String id, String label, Id icon) {
    return new PlayerPreference.Choice<>(value, key(id, label), icon, 1);
  }

  public enum ShotScope {
    ALL, BOW, CROSSBOW, ARROWS, TRIDENTS, THROWN;

    boolean accepts(Id weapon) {
      return switch (this) {
        case ALL -> true;
        case BOW -> weapon.equals(RangedPreferences.BOW);
        case CROSSBOW -> weapon.equals(RangedPreferences.CROSSBOW);
        case ARROWS -> weapon.equals(RangedPreferences.BOW) || weapon.equals(RangedPreferences.CROSSBOW);
        case TRIDENTS -> weapon.equals(RangedPreferences.TRIDENT);
        case THROWN -> !weapon.equals(RangedPreferences.BOW) && !weapon.equals(RangedPreferences.CROSSBOW)
            && !weapon.equals(RangedPreferences.TRIDENT);
      };
    }

    public boolean accepts(ProjectileRef projectile) {
      boolean arrow = RangedProjectiles.isArrowLike(projectile);
      boolean trident = projectile.type().equals(RangedPreferences.TRIDENT);
      return switch (this) {
        case ALL -> true;
        case BOW -> arrow && !trident && !projectile.shotFromCrossbow();
        case CROSSBOW -> arrow && projectile.shotFromCrossbow();
        case ARROWS -> arrow && !trident;
        case TRIDENTS -> trident;
        case THROWN -> !arrow;
      };
    }
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

  enum Items {
    ALL, BLOCKS, VALUABLES, FOOD;

    boolean accepts(Id material) {
      ItemOps items = AdaptCore.platform().host().items();
      String name = material.path();
      return switch (this) {
        case ALL -> true;
        case BLOCKS -> items.isBlock(material);
        case FOOD -> items.isEdible(material);
        case VALUABLES -> name.endsWith("_ore") || name.endsWith("_ingot")
            || name.startsWith("raw_") || material.equals(Id.minecraft("diamond")) || material.equals(Id.minecraft("emerald"))
            || material.equals(Id.minecraft("coal")) || material.equals(Id.minecraft("redstone"))
            || material.equals(Id.minecraft("lapis_lazuli"));
      };
    }
  }

  enum Preview {
    BOTH, DRAWING, SNEAKING
  }
}
