package art.arcane.adapt.content.golem.plinth;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.item.ExactIngredient;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.api.item.MaterialNames;
import art.arcane.adapt.content.golem.item.MultisetMatch;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class InfusionRecipes {
  public static final int PEDESTAL_COUNT = 4;
  public static final int MIN_DURATION_TICKS = 20;
  public static final int MAX_DURATION_TICKS = 6000;
  public static final InfusionRecipes EMPTY = new InfusionRecipes(List.of());
  private static final String MASTERWORK_HEART = "masterwork_heart";
  private static final Set<Id> AIR = Set.of(Id.minecraft("air"), Id.minecraft("cave_air"), Id.minecraft("void_air"));

  private final List<Infusion> infusions;

  private InfusionRecipes(List<Infusion> infusions) {
    this.infusions = List.copyOf(infusions);
  }

  public static InfusionRecipes bukkit(Map<String, InfusionRecipe> specs) {
    return parse(specs, bukkitResolver((Id material) -> AdaptApi.platform().host().items().isItem(material)), AdaptApi.log()::warn);
  }

  public static Resolver bukkitResolver(Predicate<Id> isItem) {
    return new BukkitResolver(isItem);
  }

  public static InfusionRecipes parse(Map<String, InfusionRecipe> specs, Resolver resolver, Consumer<String> warn) {
    if (specs == null || specs.isEmpty()) {
      return EMPTY;
    }
    List<Infusion> parsed = new ArrayList<>(specs.size());
    // Recipes are tried in id order, so an overlap resolves the same way on every start.
    for (Map.Entry<String, InfusionRecipe> entry : new TreeMap<>(specs).entrySet()) {
      InfusionRecipe spec = entry.getValue();
      if (spec == null || !spec.isEnabled()) {
        continue;
      }
      List<String> problems = new ArrayList<>();
      Infusion infusion = parse(entry.getKey(), spec, resolver, problems);
      if (infusion == null) {
        warn.accept("Skipping infusion recipe " + entry.getKey() + ": " + String.join("; ", problems));
        continue;
      }
      parsed.add(infusion);
    }
    return new InfusionRecipes(parsed);
  }

  public Optional<Infusion> match(ItemRef center, List<ItemRef> pedestals) {
    for (Infusion infusion : infusions) {
      if (infusion.center().matches(center) && MultisetMatch.matches(infusion.pedestals(), pedestals, InfusionIngredient::matches)) {
        return Optional.of(infusion);
      }
    }
    return Optional.empty();
  }

  public Optional<LevelSpan> levelSpan(Predicate<Infusion> filter) {
    int lowest = Integer.MAX_VALUE;
    int highest = Integer.MIN_VALUE;
    for (Infusion infusion : infusions) {
      if (filter.test(infusion)) {
        lowest = Math.min(lowest, infusion.essenceLevels());
        highest = Math.max(highest, infusion.essenceLevels());
      }
    }
    return lowest > highest ? Optional.empty() : Optional.of(new LevelSpan(lowest, highest));
  }

  private static Infusion parse(String id, InfusionRecipe spec, Resolver resolver, List<String> problems) {
    Id result = key(spec.getResult());
    Supplier<ItemRef> resultItem = result == null ? null : resolver.result(result);
    if (resultItem == null) {
      problems.add("result \"" + spec.getResult() + "\" must be a Golemancy rune or golemancy:masterwork_heart");
    }
    InfusionIngredient center = ingredient("center", spec.getCenter(), resolver, problems);
    List<String> pedestalSpecs = spec.getPedestals() == null ? List.of() : spec.getPedestals();
    List<InfusionIngredient> pedestals = new ArrayList<>(PEDESTAL_COUNT);
    if (pedestalSpecs.size() != PEDESTAL_COUNT) {
      problems.add("pedestals must list exactly " + PEDESTAL_COUNT + " items, found " + pedestalSpecs.size());
    } else {
      for (String pedestal : pedestalSpecs) {
        pedestals.add(ingredient("pedestal", pedestal, resolver, problems));
      }
    }
    String unlock = spec.getUnlockAdaptation() == null ? "" : spec.getUnlockAdaptation().trim();
    if (unlock.isEmpty()) {
      problems.add("unlockAdaptation is empty");
    }
    if (!problems.isEmpty()) {
      return null;
    }
    int duration = Math.max(MIN_DURATION_TICKS, Math.min(MAX_DURATION_TICKS, spec.getDurationTicks()));
    return new Infusion(id, result, resultItem, center, pedestals, Math.max(0, spec.getEssenceLevels()), duration, unlock, Math.max(1, spec.getUnlockLevel()));
  }

  private static InfusionIngredient ingredient(String subject, String raw, Resolver resolver, List<String> problems) {
    String trimmed = raw == null ? "" : raw.trim();
    if (trimmed.isEmpty()) {
      problems.add(subject + " is empty");
      return null;
    }
    InfusionIngredient ingredient;
    if (trimmed.indexOf(':') >= 0) {
      Id key = key(trimmed);
      ingredient = key == null ? null : resolver.exact(key);
    } else {
      ingredient = resolver.material(trimmed);
    }
    if (ingredient == null) {
      problems.add(subject + " \"" + trimmed + "\" is not a known item");
    }
    return ingredient;
  }

  private static Id key(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    Id key = Id.tryParse(raw.trim().toLowerCase(Locale.ROOT));
    return key == null || !validKey(key.namespace(), false) || !validKey(key.path(), true) ? null : key;
  }

  private static boolean validKey(String part, boolean path) {
    if (part.isEmpty()) {
      return false;
    }
    for (int index = 0; index < part.length(); index++) {
      char character = part.charAt(index);
      boolean allowed = (character >= 'a' && character <= 'z') || (character >= '0' && character <= '9')
          || character == '_' || character == '-' || character == '.' || (path && character == '/');
      if (!allowed) {
        return false;
      }
    }
    return true;
  }

  private static boolean known(Id material) {
    return !AdaptApi.isRunning() || AdaptApi.platform().host().registries().exists(RegistryKind.ITEM, material);
  }

  public interface Resolver {
    InfusionIngredient material(String name);

    InfusionIngredient exact(Id id);

    Supplier<ItemRef> result(Id id);
  }

  private static final class BukkitResolver implements Resolver {
    private final Predicate<Id> isItem;

    private BukkitResolver(Predicate<Id> isItem) {
      this.isItem = Objects.requireNonNull(isItem);
    }

    @Override
    public InfusionIngredient material(String name) {
      Id material = MaterialNames.exact(name.trim().toUpperCase(Locale.ROOT));
      if (material == null || !known(material) || AIR.contains(material) || !isItem.test(material)) {
        return null;
      }
      return (ItemRef item) -> item != null && !item.isEmpty() && item.type().equals(material) && !GolemItems.isGolemItem(item);
    }

    @Override
    public InfusionIngredient exact(Id id) {
      ExactIngredient ingredient = GolemItems.ingredient(id);
      return ingredient == null ? null : (ItemRef item) -> item != null && ingredient.matches().test(item);
    }

    @Override
    public Supplier<ItemRef> result(Id id) {
      if (!GolemItems.NAMESPACE.equals(id.namespace())) {
        return null;
      }
      if (id.path().equals(MASTERWORK_HEART)) {
        return GolemItems::masterworkHeart;
      }
      if (id.path().startsWith(Infusion.RUNE_PREFIX) && GolemItems.ingredient(id) != null) {
        return () -> GolemItems.resolve(id);
      }
      return null;
    }
  }
}
