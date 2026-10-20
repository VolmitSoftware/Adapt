package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.recipe.ExactIngredient;
import art.arcane.adapt.api.recipe.MultisetMatch;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
  private static final Set<Material> AIR = EnumSet.of(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR);

  private final List<Infusion> infusions;

  private InfusionRecipes(List<Infusion> infusions) {
    this.infusions = List.copyOf(infusions);
  }

  public static InfusionRecipes bukkit(Map<String, InfusionRecipe> specs) {
    return parse(specs, bukkitResolver(Material::isItem), Adapt::warn);
  }

  public static Resolver bukkitResolver(Predicate<Material> isItem) {
    return new BukkitResolver(isItem);
  }

  public static InfusionRecipes parse(Map<String, InfusionRecipe> specs, Resolver resolver, Consumer<String> warn) {
    if (specs == null || specs.isEmpty()) {
      return EMPTY;
    }
    List<Infusion> parsed = new ArrayList<>(specs.size());
    for (Map.Entry<String, InfusionRecipe> entry : specs.entrySet()) {
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

  public Optional<Infusion> match(ItemStack center, List<ItemStack> pedestals) {
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
    NamespacedKey result = key(spec.getResult());
    Supplier<ItemStack> resultItem = result == null ? null : resolver.result(result);
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
      NamespacedKey key = key(trimmed);
      ingredient = key == null ? null : resolver.exact(key);
    } else {
      ingredient = resolver.material(trimmed);
    }
    if (ingredient == null) {
      problems.add(subject + " \"" + trimmed + "\" is not a known item");
    }
    return ingredient;
  }

  private static NamespacedKey key(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return NamespacedKey.fromString(raw.trim().toLowerCase(Locale.ROOT));
    } catch (IllegalArgumentException invalid) {
      return null;
    }
  }

  public interface Resolver {
    InfusionIngredient material(String name);

    InfusionIngredient exact(NamespacedKey id);

    Supplier<ItemStack> result(NamespacedKey id);
  }

  private static final class BukkitResolver implements Resolver {
    private final Predicate<Material> isItem;

    private BukkitResolver(Predicate<Material> isItem) {
      this.isItem = Objects.requireNonNull(isItem);
    }

    @Override
    public InfusionIngredient material(String name) {
      Material material = Material.getMaterial(name.trim().toUpperCase(Locale.ROOT));
      if (material == null || material.isLegacy() || AIR.contains(material) || !isItem.test(material)) {
        return null;
      }
      return item -> item != null && item.getType() == material && !GolemItems.isGolemItem(item);
    }

    @Override
    public InfusionIngredient exact(NamespacedKey id) {
      ExactIngredient ingredient = GolemItems.ingredient(id);
      return ingredient == null ? null : item -> item != null && ingredient.matches().test(item);
    }

    @Override
    public Supplier<ItemStack> result(NamespacedKey id) {
      if (!GolemItems.NAMESPACE.equals(id.getNamespace())) {
        return null;
      }
      if (id.getKey().equals(MASTERWORK_HEART)) {
        return GolemItems::masterworkHeart;
      }
      if (id.getKey().startsWith(Infusion.RUNE_PREFIX) && GolemItems.ingredient(id) != null) {
        return () -> GolemItems.resolve(id);
      }
      return null;
    }
  }
}
