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

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MaterialValueOrderTest {
  private static final Id INGOT = Id.minecraft("iron_ingot");
  private static final Id BLOCK = Id.minecraft("iron_block");
  private static final Id ANVIL = Id.minecraft("anvil");
  private static final Id CHESTPLATE = Id.minecraft("iron_chestplate");
  private static final Id NUGGET = Id.minecraft("iron_nugget");
  private static final Id PICKAXE = Id.minecraft("iron_pickaxe");
  private static final Id RAW = Id.minecraft("raw_iron");
  private static final Id RAW_BLOCK = Id.minecraft("raw_iron_block");
  private static final Id STICK = Id.minecraft("stick");
  private static final Id PLANKS = Id.minecraft("oak_planks");
  private static final Id ORE = Id.minecraft("iron_ore");
  private static final Id DEEPSLATE_ORE = Id.minecraft("deepslate_iron_ore");

  private static final Map<Id, Double> EXPECTED = Map.of(
      INGOT, 2.381666666666667,
      BLOCK, 6.8225,
      ANVIL, 5.745638888888889,
      CHESTPLATE, 21.053333333333335,
      NUGGET, 1.4735493827160493);

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
    AdaptPorts.install(MaterialValuePort.class, new VanillaIron());
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
  void valuesMatchAFreshFirstValuationInEveryRequestOrder() {
    for (List<Id> order : permutations(List.of(INGOT, BLOCK, ANVIL, CHESTPLATE, NUGGET))) {
      MaterialValue.invalidateCache();
      for (Id material : order) {
        assertThat(MaterialValue.getValue(material)).as("%s requested in order %s", material, order)
            .isCloseTo(EXPECTED.get(material), within(1e-9));
      }
    }
  }

  @Test
  void anvilValuedFirstKeepsTheChestplateSalvageable() {
    MaterialValue.getValue(ANVIL);
    double chestplate = MaterialValue.getValue(CHESTPLATE);
    double ingot = MaterialValue.getValue(INGOT);

    assertThat(ingot * 4).isLessThan(chestplate);
  }

  private static List<List<Id>> permutations(List<Id> items) {
    if (items.isEmpty()) {
      return List.of(List.of());
    }
    List<List<Id>> result = new ArrayList<>();
    for (Id head : items) {
      List<Id> rest = new ArrayList<>(items);
      rest.remove(head);
      for (List<Id> tail : permutations(rest)) {
        List<Id> permutation = new ArrayList<>();
        permutation.add(head);
        permutation.addAll(tail);
        result.add(permutation);
      }
    }
    return result;
  }

  /** Vanilla 26.3 recipes reachable from iron, in Paper's recipe iterator order. */
  private static final class VanillaIron implements MaterialValuePort {
    private final Map<Id, List<MaterialRecipe>> recipes = new LinkedHashMap<>();

    private VanillaIron() {
      add(INGOT, 9, of(BLOCK, 1));
      add(INGOT, 1, of(NUGGET, 9));
      add(INGOT, 1, of(DEEPSLATE_ORE, 1));
      add(INGOT, 1, of(ORE, 1));
      add(INGOT, 1, of(RAW, 1));
      add(INGOT, 1, of(DEEPSLATE_ORE, 1));
      add(INGOT, 1, of(ORE, 1));
      add(INGOT, 1, of(RAW, 1));
      add(BLOCK, 1, of(INGOT, 9));
      add(ANVIL, 1, of(BLOCK, 3), of(INGOT, 4));
      add(CHESTPLATE, 1, of(INGOT, 8));
      add(NUGGET, 9, of(INGOT, 1));
      add(NUGGET, 1, of(PICKAXE, 1));
      add(NUGGET, 1, of(PICKAXE, 1));
      add(PICKAXE, 1, of(INGOT, 3), of(STICK, 2));
      add(RAW, 9, of(RAW_BLOCK, 1));
      add(RAW_BLOCK, 1, of(RAW, 9));
      add(STICK, 4, of(PLANKS, 2));
      add(STICK, 1, of(Id.minecraft("bamboo"), 2));
      add(PLANKS, 4, of(Id.minecraft("oak_log"), 1));
    }

    private static MaterialCount of(Id material, int amount) {
      return new MaterialCount(material, amount);
    }

    private void add(Id output, int outputAmount, MaterialCount... inputs) {
      List<MaterialCount> input = List.of(inputs);
      recipes.computeIfAbsent(output, (Id key) -> new ArrayList<>())
          .add(new MaterialRecipe(input, new MaterialCount(output, outputAmount)));
    }

    @Override
    public List<MaterialRecipe> recipesFor(Id material) {
      return recipes.getOrDefault(material, List.of());
    }

    @Override
    public boolean breaksInstantly(Id material) {
      return false;
    }
  }
}
