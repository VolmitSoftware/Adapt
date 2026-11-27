package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.AdaptationDamageTargets;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.value.Id;

import java.util.List;

public final class RangedPreferences {
  static final Id BOW = Id.minecraft("bow");
  static final Id CROSSBOW = Id.minecraft("crossbow");
  static final Id TRIDENT = Id.minecraft("trident");

  static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("require-sneak", key("sneak"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> GLOW = CommonPreferences.toggle("target-glow", key("glow"));
  static final PlayerPreference<CommonPreferences.Toggle> IGNORE_PASSIVE = CommonPreferences.toggle("ignore-passive", key("ignore_passive"), CommonPreferences.Toggle.OFF);
  static final PlayerPreference<CommonPreferences.Toggle> DAMPEN = CommonPreferences.toggle("dampen-velocity", key("dampen"));
  public static final PlayerPreference<CommonPreferences.Toggle> IMPACT = CommonPreferences.toggle("impact-marker", key("impact"));
  public static final PlayerPreference<CommonPreferences.Toggle> TRAIL = CommonPreferences.toggle("trajectory-trail", key("trail"));
  static final PlayerPreference<CommonPreferences.Toggle> ALL_PROJECTILES = CommonPreferences.toggle("all-projectiles", key("all_projectiles"));
  public static final PlayerPreference<ShotScope> SHOTS = PlayerPreference.of("projectiles", ShotScope.class, ShotScope.ALL, List.of(
          choice(ShotScope.ALL, "all", Id.minecraft("arrow")),
          choice(ShotScope.BOW, "bow", Id.minecraft("bow")),
          choice(ShotScope.CROSSBOW, "crossbow", Id.minecraft("crossbow")),
          choice(ShotScope.ARROWS, "arrows", Id.minecraft("spectral_arrow")),
          choice(ShotScope.TRIDENTS, "tridents", Id.minecraft("trident")),
          choice(ShotScope.THROWN, "thrown", Id.minecraft("snowball")))).labelled(key("projectiles"));
  static final PlayerPreference<Targets> TARGETS = PlayerPreference.of("targets", Targets.class, Targets.ALL, List.of(
          choice(Targets.ALL, "all", Id.minecraft("target")),
          choice(Targets.HOSTILE, "hostile", Id.minecraft("zombie_head")),
          choice(Targets.NON_PLAYERS, "non_players", Id.minecraft("player_head")))).labelled(key("targets"));
  static final PlayerPreference<Items> ITEMS = PlayerPreference.of("items", Items.class, Items.ALL, List.of(
          choice(Items.ALL, "all", Id.minecraft("chest")),
          choice(Items.BLOCKS, "blocks", Id.minecraft("stone")),
          choice(Items.VALUABLES, "valuables", Id.minecraft("diamond")),
          choice(Items.FOOD, "food", Id.minecraft("apple")))).labelled(key("items"));
  static final PlayerPreference<Preview> PREVIEW = PlayerPreference.of("preview-trigger", Preview.class, Preview.BOTH, List.of(
          choice(Preview.BOTH, "both", Id.minecraft("bow")),
          choice(Preview.DRAWING, "drawing", Id.minecraft("arrow")),
          choice(Preview.SNEAKING, "sneaking", Id.minecraft("leather_boots")))).labelled(key("preview"));

  private RangedPreferences() {
  }


  private static Text key(String id) {
    return Text.key("adapt.ranged.preferences." + id);
  }

  private static <E extends Enum<E>> PlayerPreference.Choice<E> choice(E value, String id, Id icon) {
    return new PlayerPreference.Choice<>(value, key(id), icon, 1);
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
      ItemOps items = AdaptApi.platform().host().items();
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
