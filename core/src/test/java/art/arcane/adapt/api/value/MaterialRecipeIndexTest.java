package art.arcane.adapt.api.value;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptEnvironment;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialRecipeIndexTest {
  private static final int ITEMS = 12_000;
  private static final int RECIPES = 40_000;

  @TempDir
  Path data;
  private AdaptConfig previousConfig;

  @BeforeEach
  void install() throws Exception {
    AdaptEnvironment.install(new TestPlatform.RecordingLog(), () -> data);
    Field configField = AdaptConfig.class.getDeclaredField("config");
    configField.setAccessible(true);
    previousConfig = (AdaptConfig) configField.get(null);
    configField.set(null, new AdaptConfig());
    MaterialValue.invalidateCache();
  }

  @AfterEach
  void restore() throws Exception {
    MaterialValue.invalidateCache();
    AdaptPorts.uninstall(MaterialValuePort.class);
    Field configField = AdaptConfig.class.getDeclaredField("config");
    configField.setAccessible(true);
    configField.set(null, previousConfig);
  }

  @Test
  void valuingManyDistinctItemsEnumeratesTheRegistryOnce() {
    FakeRegistry registry = new FakeRegistry(generate(ITEMS, RECIPES, 7L));
    IndexedPort port = new IndexedPort(registry);
    AdaptPorts.install(MaterialValuePort.class, port);

    for (int item = 0; item < 2_000; item++) {
      MaterialValue.getValue(item(item));
    }

    assertThat(registry.enumerations.get()).isEqualTo(1);
  }

  @Test
  void eachOutputIsConvertedOnceAcrossValuations() {
    FakeRegistry registry = new FakeRegistry(generate(2_000, 6_000, 11L));
    IndexedPort port = new IndexedPort(registry);
    AdaptPorts.install(MaterialValuePort.class, port);

    for (int item = 0; item < 2_000; item++) {
      MaterialValue.getValue(item(item));
    }
    int afterFirstPass = port.conversions.get();
    MaterialValue.invalidateCache();
    for (int item = 0; item < 2_000; item++) {
      MaterialValue.getValue(item(item));
    }

    assertThat(afterFirstPass).isLessThanOrEqualTo(6_000);
    assertThat(port.conversions.get()).isEqualTo(afterFirstPass);
    assertThat(registry.enumerations.get()).isEqualTo(1);
  }

  @Test
  void indexedValuesMatchAFullScanPerLookup() {
    List<FakeRecipe> recipes = generate(600, 1_600, 3L);
    FakeRegistry registry = new FakeRegistry(recipes);
    Map<Id, Double> scanned = new HashMap<>();
    AdaptPorts.install(MaterialValuePort.class, new ScanningPort(registry));
    for (int item = 0; item < 600; item++) {
      scanned.put(item(item), MaterialValue.getValue(item(item)));
    }

    MaterialValue.invalidateCache();
    AdaptPorts.install(MaterialValuePort.class, new IndexedPort(registry));
    for (int item = 599; item >= 0; item--) {
      assertThat(MaterialValue.getValue(item(item))).as("item %d", item).isEqualTo(scanned.get(item(item)));
    }
  }

  @Test
  void swappedRegistryRebuildsTheIndexOnce() {
    FakeRegistry first = new FakeRegistry(List.of(recipe(1, 1, 0)));
    IndexedPort port = new IndexedPort(first);

    assertThat(port.recipesFor(item(1))).hasSize(1);
    assertThat(port.recipesFor(item(2))).isEmpty();

    FakeRegistry reloaded = new FakeRegistry(List.of(recipe(2, 1, 0), recipe(2, 2, 1)));
    port.registry = reloaded;

    assertThat(port.recipesFor(item(1))).isEmpty();
    assertThat(port.recipesFor(item(2))).hasSize(2);
    assertThat(port.recipesFor(item(2))).hasSize(2);
    assertThat(first.enumerations.get()).isEqualTo(1);
    assertThat(reloaded.enumerations.get()).isEqualTo(1);
  }

  @Test
  void cyclesResolveToTheSameFiniteValuesAsAFullScan() {
    FakeRegistry registry = new FakeRegistry(List.of(
        recipe(1, 1, 2),
        recipe(2, 1, 1),
        recipe(3, 9, 1),
        recipe(1, 1, 3)));
    Map<Integer, Double> scanned = new HashMap<>();
    AdaptPorts.install(MaterialValuePort.class, new ScanningPort(registry));
    for (int item = 1; item <= 3; item++) {
      scanned.put(item, MaterialValue.getValue(item(item)));
    }
    int scans = registry.enumerations.get();

    MaterialValue.invalidateCache();
    AdaptPorts.install(MaterialValuePort.class, new IndexedPort(registry));
    for (int item = 3; item >= 1; item--) {
      double value = MaterialValue.getValue(item(item));
      assertThat(value).as("item %d", item).isFinite().isEqualTo(scanned.get(item));
    }
    assertThat(registry.enumerations.get() - scans).isEqualTo(1);
  }

  @Test
  void unknownItemsAreCachedWithoutTouchingTheRegistryAgain() {
    FakeRegistry registry = new FakeRegistry(List.of(recipe(1, 1, 0)));
    IndexedPort port = new IndexedPort(registry);
    AdaptPorts.install(MaterialValuePort.class, port);

    double first = MaterialValue.getValue(Id.minecraft("not_a_recipe_output"));
    int conversions = port.conversions.get();
    double again = MaterialValue.getValue(Id.minecraft("not_a_recipe_output"));

    assertThat(first).isEqualTo(AdaptConfig.get().getValue().getBaseValue());
    assertThat(again).isEqualTo(first);
    assertThat(port.lookups.get()).isEqualTo(1);
    assertThat(port.conversions.get()).isEqualTo(conversions);
    assertThat(registry.enumerations.get()).isEqualTo(1);
  }

  @Test
  void valuingCostStaysFlatWithRegistrySize() {
    ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    List<FakeRecipe> recipes = generate(ITEMS, RECIPES, 5L);
    FakeRegistry registry = new FakeRegistry(recipes);
    int sample = 25;

    AdaptPorts.install(MaterialValuePort.class, new ScanningPort(registry));
    long scanStart = threads.getCurrentThreadCpuTime();
    for (int item = 0; item < sample; item++) {
      MaterialValue.getValue(item(item));
    }
    long scanNanos = threads.getCurrentThreadCpuTime() - scanStart;

    MaterialValue.invalidateCache();
    AdaptPorts.install(MaterialValuePort.class, new IndexedPort(registry));
    long indexStart = threads.getCurrentThreadCpuTime();
    for (int item = 0; item < sample; item++) {
      MaterialValue.getValue(item(item));
    }
    long indexNanos = threads.getCurrentThreadCpuTime() - indexStart;

    MaterialValue.invalidateCache();
    long warmStart = threads.getCurrentThreadCpuTime();
    for (int item = 0; item < ITEMS; item++) {
      MaterialValue.getValue(item(item));
    }
    long warmNanos = threads.getCurrentThreadCpuTime() - warmStart;

    System.out.printf("valuing %d items over %d recipes: full scan %.1f ms cpu, index %.1f ms cpu (incl. build); all %d items indexed %.1f ms cpu%n",
        sample, RECIPES, scanNanos / 1e6, indexNanos / 1e6, ITEMS, warmNanos / 1e6);
    assertThat(indexNanos).isLessThan(scanNanos);
  }

  private static Id item(int index) {
    return Id.of("test", "item_" + index);
  }

  private static FakeRecipe recipe(int output, int outputAmount, int... inputs) {
    List<Id> in = new ArrayList<>();
    for (int input : inputs) {
      in.add(item(input));
    }
    return new FakeRecipe(item(output), outputAmount, in);
  }

  /**
   * Shaped like a modded registry: most inputs are a few hundred common materials, some are nearby intermediates, and
   * a few point anywhere, which forms cycles.
   */
  private static List<FakeRecipe> generate(int items, int recipes, long seed) {
    Random random = new Random(seed);
    int common = Math.min(300, items);
    List<FakeRecipe> generated = new ArrayList<>(recipes);
    for (int index = 0; index < recipes; index++) {
      int output = random.nextInt(items);
      int inputCount = 1 + random.nextInt(3);
      List<Id> inputs = new ArrayList<>(inputCount);
      for (int input = 0; input < inputCount; input++) {
        int roll = random.nextInt(100);
        int chosen;
        if (roll < 2 || output == 0) {
          chosen = random.nextInt(items);
        } else if (output < common) {
          chosen = output - 1 - random.nextInt(Math.min(output, 40));
        } else if (roll < 7) {
          chosen = output - 1 - random.nextInt(Math.min(output, 500));
        } else {
          chosen = random.nextInt(common);
        }
        inputs.add(item(chosen));
      }
      generated.add(new FakeRecipe(item(output), 1 + random.nextInt(4), inputs));
    }
    return generated;
  }

  private record FakeRecipe(Id output, int outputAmount, List<Id> inputs) {
    MaterialRecipe toMaterial() {
      Map<Id, Integer> counts = new LinkedHashMap<>();
      for (Id input : inputs) {
        counts.merge(input, 1, Integer::sum);
      }
      List<MaterialCount> in = new ArrayList<>(counts.size());
      counts.forEach((Id id, Integer amount) -> in.add(new MaterialCount(id, amount)));
      return new MaterialRecipe(in, new MaterialCount(output, outputAmount));
    }
  }

  private static final class FakeRegistry implements Iterable<FakeRecipe> {
    private final List<FakeRecipe> recipes;
    private final AtomicInteger enumerations = new AtomicInteger();

    private FakeRegistry(List<FakeRecipe> recipes) {
      this.recipes = recipes;
    }

    @Override
    public Iterator<FakeRecipe> iterator() {
      enumerations.incrementAndGet();
      return recipes.iterator();
    }
  }

  private static final class IndexedPort implements MaterialValuePort {
    private final AtomicInteger conversions = new AtomicInteger();
    private final AtomicInteger lookups = new AtomicInteger();
    private final MaterialRecipeIndex<Id, FakeRecipe> index = new MaterialRecipeIndex<>(
        FakeRecipe::output,
        (FakeRecipe recipe) -> {
          conversions.incrementAndGet();
          return recipe.toMaterial();
        });
    private FakeRegistry registry;

    private IndexedPort(FakeRegistry registry) {
      this.registry = registry;
    }

    @Override
    public List<MaterialRecipe> recipesFor(Id material) {
      lookups.incrementAndGet();
      FakeRegistry current = registry;
      return index.recipesFor(current, () -> current, material);
    }

    @Override
    public boolean breaksInstantly(Id material) {
      return false;
    }
  }

  /** The lookup every port used before the index: walk the whole registry for each material. */
  private static final class ScanningPort implements MaterialValuePort {
    private final FakeRegistry registry;

    private ScanningPort(FakeRegistry registry) {
      this.registry = registry;
    }

    @Override
    public List<MaterialRecipe> recipesFor(Id material) {
      List<MaterialRecipe> found = new ArrayList<>();
      for (FakeRecipe recipe : registry) {
        if (recipe.output().equals(material)) {
          found.add(recipe.toMaterial());
        }
      }
      return found;
    }

    @Override
    public boolean breaksInstantly(Id material) {
      return false;
    }
  }
}
