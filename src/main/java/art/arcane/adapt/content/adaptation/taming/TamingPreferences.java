package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.adaptation.AdaptationDamageTargets;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Material;
import org.bukkit.DyeColor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
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
          choice(Pets.ALL, "all", "All permitted pets", Material.LEAD),
          choice(Pets.WOLVES, "wolves", "Wolves only", Material.BONE),
          choice(Pets.CATS, "cats", "Cats only", Material.COD),
          choice(Pets.HORSES, "horses", "Horses and equines", Material.SADDLE))));
  static final PlayerPreference<Targets> TARGETS = new PlayerPreference<>(Targets.class,
      new PlayerPreference.Definition<>("targets", key("targets", "Command targets"), Targets.ALL, List.of(
          choice(Targets.ALL, "all_targets", "All permitted targets", Material.DIAMOND_SWORD),
          choice(Targets.HOSTILE, "hostile", "Hostile mobs", Material.ZOMBIE_HEAD),
          choice(Targets.NON_PLAYERS, "non_players", "Non-player targets", Material.BONE))));
  static final PlayerPreference<Reserve> BONES = new PlayerPreference<>(Reserve.class,
      new PlayerPreference.Definition<>("bone-reserve", key("bones", "Bones remaining after command"), Reserve.NONE, List.of(
          choice(Reserve.NONE, "none", "No extra reserve", Material.BONE),
          choice(Reserve.FOUR, "four", "Keep 4", Material.BONE_BLOCK),
          choice(Reserve.EIGHT, "eight", "Keep 8", Material.CHEST))));
  static final PlayerPreference<Reserve> HUNGER = new PlayerPreference<>(Reserve.class,
      new PlayerPreference.Definition<>("hunger-reserve", key("hunger", "Hunger remaining after recall"), Reserve.NONE, List.of(
          choice(Reserve.NONE, "none", "No extra reserve", Material.BOWL),
          choice(Reserve.FOUR, "four", "Keep 4", Material.APPLE),
          choice(Reserve.EIGHT, "eight", "Keep 8", Material.BREAD))));
  static final PlayerPreference<Health> HEALTH = new PlayerPreference<>(Health.class,
      new PlayerPreference.Definition<>("pet-health-reserve", key("health", "Pet health remaining after protection"), Health.DEFAULT, List.of(
          choice(Health.DEFAULT, "server_health", "Server minimum", Material.BONE),
          choice(Health.HALF, "half", "At least 50% health", Material.APPLE),
          choice(Health.THREE_QUARTERS, "three_quarters", "At least 75% health", Material.GOLDEN_APPLE))));
  static final PlayerPreference<Items> ITEMS = new PlayerPreference<>(Items.class,
      new PlayerPreference.Definition<>("items", key("items", "Items to fetch"), Items.ALL, List.of(
          choice(Items.ALL, "all_items", "All permitted items", Material.CHEST),
          choice(Items.FOOD, "food", "Food only", Material.COOKED_BEEF),
          choice(Items.BLOCKS, "blocks", "Blocks only", Material.STONE),
          choice(Items.VALUABLES, "valuables", "Ores and minerals", Material.DIAMOND))));
  static final PlayerPreference<Collar> COLLAR = new PlayerPreference<>(Collar.class,
      new PlayerPreference.Definition<>("fetch-collar", key("collar", "Wolves allowed to fetch"), Collar.ALL, List.of(
          choice(Collar.ALL, "all_collars", "All collar colors", Material.LEAD),
          choice(Collar.RED, "red", "Red collars", Material.RED_DYE),
          choice(Collar.BLUE, "blue", "Blue collars", Material.BLUE_DYE),
          choice(Collar.YELLOW, "yellow", "Yellow collars", Material.YELLOW_DYE))));

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
  private static <E extends Enum<E>> PlayerPreference.Choice<E> choice(E value, String id, String text, Material icon) {
    return new PlayerPreference.Choice<>(value, key(id, text), icon, 1);
  }
  enum Pets {
    ALL, WOLVES, CATS, HORSES;
    boolean accepts(EntityType type) {
      return switch (this) {
        case ALL -> true;
        case WOLVES -> type == EntityType.WOLF;
        case CATS -> type == EntityType.CAT;
        case HORSES -> type == EntityType.HORSE || type == EntityType.DONKEY || type == EntityType.MULE || type == EntityType.SKELETON_HORSE || type == EntityType.ZOMBIE_HORSE;
      };
    }
  }
  enum Targets {
    ALL, HOSTILE, NON_PLAYERS;
    boolean accepts(LivingEntity target) {
      return this == ALL || (!(target instanceof Player) && (this == NON_PLAYERS || AdaptationDamageTargets.allows(target, true)));
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
    boolean accepts(Material material) {
      return switch (this) {
        case ALL -> true;
        case FOOD -> material.isEdible();
        case BLOCKS -> material.isBlock();
        case VALUABLES -> material.name().endsWith("_ORE") || material.name().endsWith("_INGOT") || material.name().startsWith("RAW_") || material == Material.DIAMOND || material == Material.EMERALD || material == Material.COAL || material == Material.REDSTONE || material == Material.LAPIS_LAZULI;
      };
    }
  }
  enum Collar {
    ALL, RED, BLUE, YELLOW;
    boolean accepts(DyeColor color) { return this == ALL || name().equals(color.name()); }
  }
}
