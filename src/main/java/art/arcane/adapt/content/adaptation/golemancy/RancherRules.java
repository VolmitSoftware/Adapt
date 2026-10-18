package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.node.NodeRecord;
import org.bukkit.entity.Animals;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class RancherRules {
  static final String BREED = "breed";
  static final String SHEAR = "shear";
  static final String MILK = "milk";
  static final String CULL = "cull";
  static final String SPECIES_KEY = "species";
  static final String KEEP_KEY = "keep";
  static final int MIN_KEEP = 2;
  static final int MAX_KEEP = 64;
  static final int LOVE_TICKS = 600;
  static final List<String> DEFAULT_SPECIES = List.of("COW", "MOOSHROOM", "SHEEP", "PIG", "CHICKEN", "GOAT", "RABBIT");

  private static final Comparator<Choice> NEAREST = Comparator.comparingDouble(choice -> choice.beast().distanceSquared());

  private RancherRules() {
  }

  static boolean unlocked(int level, int required) {
    return level >= 1 && level >= required;
  }

  static boolean allows(Action action, int level, GolemBody body, Levels levels) {
    return switch (action) {
      case BREED -> unlocked(level, 1);
      case SHEAR -> unlocked(level, levels.shear());
      case MILK -> unlocked(level, levels.milk());
      case CULL -> body == GolemBody.IRON && unlocked(level, levels.cull());
    };
  }

  static Set<Action> actions(NodeRecord record, int level, GolemBody body, Levels levels) {
    Set<Action> actions = EnumSet.noneOf(Action.class);
    for (Action action : Action.values()) {
      if (enabled(record, action) && allows(action, level, body, levels)) {
        actions.add(action);
      }
    }
    return actions;
  }

  static int keep(NodeRecord record, int fallback) {
    String stored = record.data().get(KEEP_KEY);
    if (stored == null) {
      return clampKeep(fallback);
    }
    try {
      return clampKeep(Integer.parseInt(stored.trim()));
    } catch (NumberFormatException ignored) {
      return clampKeep(fallback);
    }
  }

  static NodeRecord adjustKeep(NodeRecord record, int delta, int fallback) {
    return withData(record, KEEP_KEY, Integer.toString(clampKeep(keep(record, fallback) + delta)));
  }

  static int clampKeep(int keep) {
    return Math.clamp(keep, MIN_KEEP, MAX_KEEP);
  }

  static Optional<EntityType> species(NodeRecord record) {
    String stored = record.data().get(SPECIES_KEY);
    return stored == null ? Optional.empty() : animal(stored);
  }

  static NodeRecord cycleSpecies(NodeRecord record, List<EntityType> tended, boolean forward) {
    int index = species(record).map(tended::indexOf).orElse(-1);
    int slots = tended.size() + 1;
    int next = Math.floorMod(index + 1 + (forward ? 1 : -1), slots) - 1;
    if (next < 0) {
      Map<String, String> data = new LinkedHashMap<>(record.data());
      data.remove(SPECIES_KEY);
      return record.toBuilder().data(data).build();
    }
    return withData(record, SPECIES_KEY, tended.get(next).name());
  }

  static boolean tends(NodeRecord record, Collection<EntityType> tended, EntityType type) {
    return tended.contains(type) && species(record).map(type::equals).orElse(true);
  }

  static boolean enabled(NodeRecord record, Action action) {
    String stored = record.data().get(action.id());
    return stored == null ? action.onByDefault() : Boolean.parseBoolean(stored);
  }

  static NodeRecord toggle(NodeRecord record, Action action) {
    return withData(record, action.id(), Boolean.toString(!enabled(record, action)));
  }

  static List<EntityType> parseSpecies(List<String> names) {
    List<EntityType> parsed = new ArrayList<>(names.size());
    for (String name : names) {
      if (name == null) {
        continue;
      }
      animal(name).filter(type -> !parsed.contains(type)).ifPresent(parsed::add);
    }
    return List.copyOf(parsed);
  }

  static List<Choice> choose(List<Beast> herd, int keep, Set<Action> actions, Supplies supplies) {
    Map<EntityType, List<Beast>> bySpecies = new EnumMap<>(EntityType.class);
    for (Beast beast : herd) {
      bySpecies.computeIfAbsent(beast.species(), ignored -> new ArrayList<>()).add(beast);
    }
    List<Choice> culls = new ArrayList<>();
    List<Choice> breeds = new ArrayList<>();
    List<Choice> shears = new ArrayList<>();
    List<Choice> milks = new ArrayList<>();
    for (Map.Entry<EntityType, List<Beast>> species : bySpecies.entrySet()) {
      List<Beast> group = species.getValue();
      int adults = adults(group);
      if (actions.contains(Action.CULL)) {
        addMatching(culls, Action.CULL, group, adults, keep);
      }
      if (actions.contains(Action.BREED) && adults < keep) {
        addBreeding(breeds, group, supplies.food(species.getKey()));
      }
      if (actions.contains(Action.SHEAR)) {
        addMatching(shears, Action.SHEAR, group, adults, keep);
      }
      if (actions.contains(Action.MILK)) {
        addMilking(milks, group, supplies);
      }
    }
    List<Choice> choices = new ArrayList<>(culls.size() + breeds.size() + shears.size() + milks.size());
    for (List<Choice> kind : List.of(culls, breeds, shears, milks)) {
      kind.sort(NEAREST);
      choices.addAll(kind);
    }
    return choices;
  }

  static int adults(List<Beast> herd) {
    int adults = 0;
    for (Beast beast : herd) {
      if (beast.adult()) {
        adults++;
      }
    }
    return adults;
  }

  static boolean fits(Action action, Beast beast, int adults, int keep) {
    if (beast.untouchable() || !beast.adult()) {
      return false;
    }
    return switch (action) {
      case BREED -> beast.breedable() && adults < keep;
      case SHEAR -> beast.shearable();
      case MILK -> beast.milk() != Milk.NONE;
      case CULL -> !beast.guarded() && adults > keep;
    };
  }

  static int cooldown(int age, double multiplier) {
    return age <= 0 ? age : (int) Math.floor(age * multiplier);
  }

  static double fraction(double value) {
    return Double.isFinite(value) ? Math.clamp(value, 0D, 1D) : 1D;
  }

  static double nonNegative(double value) {
    return Double.isFinite(value) ? Math.max(0D, value) : 0D;
  }

  private static void addMatching(List<Choice> choices, Action action, List<Beast> group, int adults, int keep) {
    for (Beast beast : group) {
      if (fits(action, beast, adults, keep)) {
        choices.add(new Choice(action, beast));
      }
    }
  }

  private static void addBreeding(List<Choice> choices, List<Beast> group, int food) {
    int waiting = 0;
    List<Beast> ready = new ArrayList<>(group.size());
    for (Beast beast : group) {
      if (beast.untouchable() || !beast.adult()) {
        continue;
      }
      if (beast.inLove()) {
        waiting++;
      } else if (beast.breedable()) {
        ready.add(beast);
      }
    }
    boolean partnerWanted = waiting % 2 == 1;
    if (partnerWanted ? food < 1 || ready.isEmpty() : food < 2 || ready.size() < 2) {
      return;
    }
    for (Beast beast : ready) {
      choices.add(new Choice(Action.BREED, beast));
    }
  }

  private static void addMilking(List<Choice> choices, List<Beast> group, Supplies supplies) {
    for (Beast beast : group) {
      if (beast.untouchable() || !beast.adult()) {
        continue;
      }
      boolean milkable = switch (beast.milk()) {
        case NONE -> false;
        case BUCKET -> supplies.buckets() > 0;
        case BUCKET_OR_BOWL -> supplies.buckets() > 0 || supplies.bowls() > 0;
      };
      if (milkable) {
        choices.add(new Choice(Action.MILK, beast));
      }
    }
  }

  private static Optional<EntityType> animal(String name) {
    EntityType type;
    try {
      type = EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return Optional.empty();
    }
    Class<?> entityClass = type.getEntityClass();
    return entityClass != null && Animals.class.isAssignableFrom(entityClass) ? Optional.of(type) : Optional.empty();
  }

  private static NodeRecord withData(NodeRecord record, String key, String value) {
    Map<String, String> data = new LinkedHashMap<>(record.data());
    data.put(key, value);
    return record.toBuilder().data(data).build();
  }

  enum Action {
    BREED(RancherRules.BREED, true),
    SHEAR(RancherRules.SHEAR, true),
    MILK(RancherRules.MILK, true),
    CULL(RancherRules.CULL, false);

    private final String id;
    private final boolean onByDefault;

    Action(String id, boolean onByDefault) {
      this.id = id;
      this.onByDefault = onByDefault;
    }

    static Optional<Action> of(String id) {
      for (Action action : values()) {
        if (action.id.equals(id)) {
          return Optional.of(action);
        }
      }
      return Optional.empty();
    }

    String id() {
      return id;
    }

    boolean onByDefault() {
      return onByDefault;
    }
  }

  enum Milk {
    NONE,
    BUCKET,
    BUCKET_OR_BOWL
  }

  interface Supplies {
    int food(EntityType species);

    int buckets();

    int bowls();
  }

  record Levels(int shear, int milk, int cull) {
  }

  record Beast(UUID id, EntityType species, double distanceSquared, boolean adult, boolean breedable, boolean inLove,
               boolean shearable, Milk milk, boolean guarded, boolean untouchable) {
  }

  record Choice(Action action, Beast beast) {
  }
}
