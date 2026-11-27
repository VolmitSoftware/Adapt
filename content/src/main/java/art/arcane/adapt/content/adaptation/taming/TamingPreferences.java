package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.AdaptationDamageTargets;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import java.util.List;
import java.util.Set;

public final class TamingPreferences {
  public static final PlayerPreference<CommonPreferences.Toggle> SPEED = CommonPreferences.toggle("speed", key("speed"));
  public static final PlayerPreference<CommonPreferences.Toggle> VISUALS = CommonPreferences.toggle("visuals", key("visuals"));
  public static final PlayerPreference<CommonPreferences.Toggle> SITTING = CommonPreferences.toggle("sitting", key("sitting"));
  public static final PlayerPreference<CommonPreferences.Toggle> HANDLING = CommonPreferences.toggle("handling", key("handling"));
  public static final PlayerPreference<CommonPreferences.Toggle> COMBAT = CommonPreferences.toggle("combat", key("combat"));
  public static final PlayerPreference<CommonPreferences.Toggle> REGEN = CommonPreferences.toggle("regeneration", key("regeneration"));
  public static final PlayerPreference<CommonPreferences.Toggle> TAMING = CommonPreferences.toggle("taming", key("taming"));
  public static final PlayerPreference<CommonPreferences.Toggle> PACIFY = CommonPreferences.toggle("pacify", key("pacify"));
  public static final PlayerPreference<Pets> PETS = PlayerPreference.of("pets", Pets.class, Pets.ALL, List.of(
          choice(Pets.ALL, "all", Id.minecraft("lead")),
          choice(Pets.WOLVES, "wolves", Id.minecraft("bone")),
          choice(Pets.CATS, "cats", Id.minecraft("cod")),
          choice(Pets.HORSES, "horses", Id.minecraft("saddle")))).labelled(key("pets"));
  public static final PlayerPreference<Targets> TARGETS = PlayerPreference.of("targets", Targets.class, Targets.ALL, List.of(
          choice(Targets.ALL, "all_targets", Id.minecraft("diamond_sword")),
          choice(Targets.HOSTILE, "hostile", Id.minecraft("zombie_head")),
          choice(Targets.NON_PLAYERS, "non_players", Id.minecraft("bone")))).labelled(key("targets"));
  public static final PlayerPreference<Reserve> BONES = PlayerPreference.of("bone-reserve", Reserve.class, Reserve.NONE, List.of(
          choice(Reserve.NONE, "none", Id.minecraft("bone")),
          choice(Reserve.FOUR, "four", Id.minecraft("bone_block")),
          choice(Reserve.EIGHT, "eight", Id.minecraft("chest")))).labelled(key("bones"));
  public static final PlayerPreference<Reserve> HUNGER = PlayerPreference.of("hunger-reserve", Reserve.class, Reserve.NONE, List.of(
          choice(Reserve.NONE, "none", Id.minecraft("bowl")),
          choice(Reserve.FOUR, "four", Id.minecraft("apple")),
          choice(Reserve.EIGHT, "eight", Id.minecraft("bread")))).labelled(key("hunger"));
  public static final PlayerPreference<Health> HEALTH = PlayerPreference.of("pet-health-reserve", Health.class, Health.DEFAULT, List.of(
          choice(Health.DEFAULT, "server_health", Id.minecraft("bone")),
          choice(Health.HALF, "half", Id.minecraft("apple")),
          choice(Health.THREE_QUARTERS, "three_quarters", Id.minecraft("golden_apple")))).labelled(key("health"));
  public static final PlayerPreference<Items> ITEMS = PlayerPreference.of("items", Items.class, Items.ALL, List.of(
          choice(Items.ALL, "all_items", Id.minecraft("chest")),
          choice(Items.FOOD, "food", Id.minecraft("cooked_beef")),
          choice(Items.BLOCKS, "blocks", Id.minecraft("stone")),
          choice(Items.VALUABLES, "valuables", Id.minecraft("diamond")))).labelled(key("items"));
  public static final PlayerPreference<Collar> COLLAR = PlayerPreference.of("fetch-collar", Collar.class, Collar.ALL, List.of(
          choice(Collar.ALL, "all_collars", Id.minecraft("lead")),
          choice(Collar.RED, "red", Id.minecraft("red_dye")),
          choice(Collar.BLUE, "blue", Id.minecraft("blue_dye")),
          choice(Collar.YELLOW, "yellow", Id.minecraft("yellow_dye")))).labelled(key("collar"));

  private TamingPreferences() {}
  private static Text key(String id) {
    return Text.key("adapt.taming.preferences." + id);
  }
  private static <E extends Enum<E>> PlayerPreference.Choice<E> choice(E value, String id, Id icon) {
    return new PlayerPreference.Choice<>(value, key(id), icon, 1);
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
      return AdaptApi.platform().host().items();
    }
  }
  enum Collar {
    ALL, RED, BLUE, YELLOW;
    boolean accepts(DyeColor color) { return this == ALL || (color != null && name().equals(color.name())); }
  }
}
