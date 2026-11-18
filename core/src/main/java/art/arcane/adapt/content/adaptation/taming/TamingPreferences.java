package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.adaptation.AdaptationDamageTargets;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TamingPreferences {
  static final PlayerPreference<CommonPreferences.Toggle> SPEED = CommonPreferences.toggle("speed", key("speed", "Speed bonus"));
  static final PlayerPreference<CommonPreferences.Toggle> VISUALS = CommonPreferences.toggle("visuals", key("visuals", "Bond particles and glow"));
  static final PlayerPreference<CommonPreferences.Toggle> SITTING = CommonPreferences.toggle("sitting", key("sitting", "Include sitting pets"));
  static final PlayerPreference<CommonPreferences.Toggle> HANDLING = CommonPreferences.toggle("handling", key("handling", "Mount speed and jump"));
  static final PlayerPreference<CommonPreferences.Toggle> COMBAT = CommonPreferences.toggle("combat", key("combat", "Mounted combat bonuses"));
  static final PlayerPreference<CommonPreferences.Toggle> REGEN = CommonPreferences.toggle("regeneration", key("regeneration", "Pack regeneration"));
  static final PlayerPreference<CommonPreferences.Toggle> TAMING = CommonPreferences.toggle("taming", key("taming", "Improved taming"));
  static final PlayerPreference<CommonPreferences.Toggle> PACIFY = CommonPreferences.toggle("pacify", key("pacify", "Pacify neutral mobs"));
  static final PlayerPreference<Pets> PETS = new PlayerPreference<>(Pets.class,
      new PlayerPreference.Definition<>("pets", key("pets", "Eligible pet types"), Pets.ALL, List.of(
          choice(Pets.ALL, "all", "All permitted pets", Id.minecraft("lead")),
          choice(Pets.WOLVES, "wolves", "Wolves only", Id.minecraft("bone")),
          choice(Pets.CATS, "cats", "Cats only", Id.minecraft("cod")),
          choice(Pets.HORSES, "horses", "Horses and equines", Id.minecraft("saddle")))));
  static final PlayerPreference<Targets> TARGETS = new PlayerPreference<>(Targets.class,
      new PlayerPreference.Definition<>("targets", key("targets", "Command targets"), Targets.ALL, List.of(
          choice(Targets.ALL, "all_targets", "All permitted targets", Id.minecraft("diamond_sword")),
          choice(Targets.HOSTILE, "hostile", "Hostile mobs", Id.minecraft("zombie_head")),
          choice(Targets.NON_PLAYERS, "non_players", "Non-player targets", Id.minecraft("bone")))));
  static final PlayerPreference<Reserve> BONES = new PlayerPreference<>(Reserve.class,
      new PlayerPreference.Definition<>("bone-reserve", key("bones", "Bones remaining after command"), Reserve.NONE, List.of(
          choice(Reserve.NONE, "none", "No extra reserve", Id.minecraft("bone")),
          choice(Reserve.FOUR, "four", "Keep 4", Id.minecraft("bone_block")),
          choice(Reserve.EIGHT, "eight", "Keep 8", Id.minecraft("chest")))));
  static final PlayerPreference<Reserve> HUNGER = new PlayerPreference<>(Reserve.class,
      new PlayerPreference.Definition<>("hunger-reserve", key("hunger", "Hunger remaining after recall"), Reserve.NONE, List.of(
          choice(Reserve.NONE, "none", "No extra reserve", Id.minecraft("bowl")),
          choice(Reserve.FOUR, "four", "Keep 4", Id.minecraft("apple")),
          choice(Reserve.EIGHT, "eight", "Keep 8", Id.minecraft("bread")))));
  static final PlayerPreference<Health> HEALTH = new PlayerPreference<>(Health.class,
      new PlayerPreference.Definition<>("pet-health-reserve", key("health", "Pet health remaining after protection"), Health.DEFAULT, List.of(
          choice(Health.DEFAULT, "server_health", "Server minimum", Id.minecraft("bone")),
          choice(Health.HALF, "half", "At least 50% health", Id.minecraft("apple")),
          choice(Health.THREE_QUARTERS, "three_quarters", "At least 75% health", Id.minecraft("golden_apple")))));
  static final PlayerPreference<Items> ITEMS = new PlayerPreference<>(Items.class,
      new PlayerPreference.Definition<>("items", key("items", "Items to fetch"), Items.ALL, List.of(
          choice(Items.ALL, "all_items", "All permitted items", Id.minecraft("chest")),
          choice(Items.FOOD, "food", "Food only", Id.minecraft("cooked_beef")),
          choice(Items.BLOCKS, "blocks", "Blocks only", Id.minecraft("stone")),
          choice(Items.VALUABLES, "valuables", "Ores and minerals", Id.minecraft("diamond")))));
  static final PlayerPreference<Collar> COLLAR = new PlayerPreference<>(Collar.class,
      new PlayerPreference.Definition<>("fetch-collar", key("collar", "Wolves allowed to fetch"), Collar.ALL, List.of(
          choice(Collar.ALL, "all_collars", "All collar colors", Id.minecraft("lead")),
          choice(Collar.RED, "red", "Red collars", Id.minecraft("red_dye")),
          choice(Collar.BLUE, "blue", "Blue collars", Id.minecraft("blue_dye")),
          choice(Collar.YELLOW, "yellow", "Yellow collars", Id.minecraft("yellow_dye")))));

  private TamingPreferences() {}
  public static void addMessages(MessageCatalog.Builder builder) {
    Set<String> added = new HashSet<>();
    for (PlayerPreference<?> preference : List.of(SPEED, VISUALS, SITTING, HANDLING, COMBAT, REGEN, TAMING, PACIFY, PETS, TARGETS, BONES, HUNGER, HEALTH, ITEMS, COLLAR)) {
      if (added.add(preference.label().id())) { builder.add(preference.label()); }
      for (PlayerPreference.Choice<?> choice : preference.choices()) {
        if (choice.label().id().startsWith("taming.preferences.") && added.add(choice.label().id())) { builder.add(choice.label()); }
      }
    }
  }
  private static TextKey key(String id, String text) { return TextKey.of("taming.preferences." + id, text); }
  private static <E extends Enum<E>> PlayerPreference.Choice<E> choice(E value, String id, String text, Id icon) {
    return new PlayerPreference.Choice<>(value, key(id, text), icon, 1);
  }
  enum Pets {
    ALL, WOLVES, CATS, HORSES;
    private static final Id WOLF = Id.minecraft("wolf");
    private static final Id CAT = Id.minecraft("cat");
    private static final Set<Id> EQUINES = Set.of(Id.minecraft("horse"), Id.minecraft("donkey"), Id.minecraft("mule"),
        Id.minecraft("skeleton_horse"), Id.minecraft("zombie_horse"));

    boolean accepts(Id type) {
      return switch (this) {
        case ALL -> true;
        case WOLVES -> WOLF.equals(type);
        case CATS -> CAT.equals(type);
        case HORSES -> EQUINES.contains(type);
      };
    }
  }
  enum Targets {
    ALL, HOSTILE, NON_PLAYERS;
    boolean accepts(LivingRef target) {
      return this == ALL || (!(target instanceof PlayerRef) && (this == NON_PLAYERS || AdaptationDamageTargets.allows(target, true)));
    }
  }
  enum Reserve {
    NONE(0), FOUR(4), EIGHT(8);
    private final int remaining;
    Reserve(int remaining) { this.remaining = remaining; }
    boolean permits(int available, int cost) { return available - cost >= remaining; }
  }
  enum Health {
    DEFAULT(0D), HALF(0.5D), THREE_QUARTERS(0.75D);
    private final double fraction;
    Health(double fraction) { this.fraction = fraction; }
    double floor(double maximum, double serverMinimum) { return Math.max(serverMinimum, maximum * fraction); }
  }
  enum Items {
    ALL, FOOD, BLOCKS, VALUABLES;
    private static final Set<Id> MINERALS = Set.of(Id.minecraft("diamond"), Id.minecraft("emerald"), Id.minecraft("coal"),
        Id.minecraft("redstone"), Id.minecraft("lapis_lazuli"));

    boolean accepts(Id material) {
      return switch (this) {
        case ALL -> true;
        case FOOD -> items().isEdible(material);
        case BLOCKS -> items().isBlock(material);
        case VALUABLES -> material.path().endsWith("_ore") || material.path().endsWith("_ingot") || material.path().startsWith("raw_") || MINERALS.contains(material);
      };
    }

    private static ItemOps items() {
      return AdaptCore.platform().host().items();
    }
  }
  enum Collar {
    ALL, RED, BLUE, YELLOW;
    boolean accepts(DyeColor color) { return this == ALL || (color != null && name().equals(color.name())); }
  }
}
