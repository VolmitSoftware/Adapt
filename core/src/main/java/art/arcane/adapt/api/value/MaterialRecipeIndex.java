package art.arcane.adapt.api.value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Server recipes grouped by output in a single pass over the recipe registry. The grouping is rebuilt only when the
 * caller hands in a different registry object (a reload or a replacement swaps it), and each output's recipes are
 * converted on first request and kept until then. Recipes keep registry order within an output.
 *
 * @param <K> output key, the platform's item type
 * @param <R> the platform's recipe type
 */
public final class MaterialRecipeIndex<K, R> {
  private final Function<R, K> output;
  private final Function<R, MaterialRecipe> converter;
  private volatile Snapshot<K, R> snapshot;

  /**
   * @param output    the recipe's output key, or null to leave the recipe out of the index
   * @param converter the recipe as a value recipe, or null when it has no usable inputs
   */
  public MaterialRecipeIndex(Function<R, K> output, Function<R, MaterialRecipe> converter) {
    this.output = output;
    this.converter = converter;
  }

  /** Grouping only, for callers that read {@link #recipes} and never convert. */
  public MaterialRecipeIndex(Function<R, K> output) {
    this(output, (R recipe) -> null);
  }

  /** Every indexed recipe with this output, unconverted, in registry order. */
  public List<R> recipes(Object registry, Supplier<? extends Iterable<? extends R>> recipes, K key) {
    List<R> raw = snapshot(registry, recipes).byOutput.get(key);
    return raw == null ? List.of() : raw;
  }

  /**
   * @param registry identity of the recipe registry the server holds now
   * @param recipes  every recipe in {@code registry}, enumerated only when {@code registry} changed
   */
  public List<MaterialRecipe> recipesFor(Object registry, Supplier<? extends Iterable<? extends R>> recipes, K key) {
    Snapshot<K, R> current = snapshot(registry, recipes);
    List<MaterialRecipe> converted = current.converted.get(key);
    if (converted != null) {
      return converted;
    }
    List<R> raw = current.byOutput.get(key);
    if (raw == null) {
      return List.of();
    }
    List<MaterialRecipe> built = new ArrayList<>(raw.size());
    for (R recipe : raw) {
      MaterialRecipe recipeValue = converter.apply(recipe);
      if (recipeValue != null) {
        built.add(recipeValue);
      }
    }
    List<MaterialRecipe> stored = List.copyOf(built);
    List<MaterialRecipe> previous = current.converted.putIfAbsent(key, stored);
    return previous != null ? previous : stored;
  }

  private Snapshot<K, R> snapshot(Object registry, Supplier<? extends Iterable<? extends R>> recipes) {
    Snapshot<K, R> current = snapshot;
    if (current != null && current.registry == registry) {
      return current;
    }
    synchronized (this) {
      current = snapshot;
      if (current != null && current.registry == registry) {
        return current;
      }
      Map<K, List<R>> byOutput = new HashMap<>();
      for (R recipe : recipes.get()) {
        K key = output.apply(recipe);
        if (key != null) {
          byOutput.computeIfAbsent(key, (K ignored) -> new ArrayList<>(1)).add(recipe);
        }
      }
      byOutput.replaceAll((K key, List<R> grouped) -> List.copyOf(grouped));
      current = new Snapshot<>(registry, byOutput);
      snapshot = current;
      return current;
    }
  }

  private static final class Snapshot<K, R> {
    private final Object registry;
    private final Map<K, List<R>> byOutput;
    private final Map<K, List<MaterialRecipe>> converted = new ConcurrentHashMap<>();

    private Snapshot(Object registry, Map<K, List<R>> byOutput) {
      this.registry = registry;
      this.byOutput = byOutput;
    }
  }
}
