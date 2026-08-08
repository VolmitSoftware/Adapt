package art.arcane.adapt.api.value;

import art.arcane.adapt.AdaptConfig;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialValueTest {
  @Test
  void appliesConfiguredMultiplierToEveryResolvedValue() {
    Map<String, Double> multipliers = Map.of("DIAMOND", 5D);

    assertThat(MaterialValue.applyMultiplier(Material.DIAMOND, 2D, multipliers)).isEqualTo(10D);
    assertThat(MaterialValue.applyMultiplier(Material.STONE, 2D, multipliers)).isEqualTo(2D);
  }

  @Test
  void acceptsCaseInsensitiveMaterialKeys() {
    Map<String, Double> multipliers = Map.of("diamond", 3D);

    assertThat(MaterialValue.getMultiplier(Material.DIAMOND, multipliers)).isEqualTo(3D);
  }

  @Test
  void convertsLoadedCacheDataToAConcurrentNullFreeMap() {
    Map<Material, Double> loaded = new HashMap<>();
    loaded.put(Material.DIAMOND, 5D);
    loaded.put(Material.STONE, null);

    Map<Material, Double> concurrent = MaterialValue.toConcurrentValueMap(loaded);

    assertThat(concurrent)
        .isInstanceOf(ConcurrentHashMap.class)
        .containsOnlyKeys(Material.DIAMOND)
        .containsEntry(Material.DIAMOND, 5D);
  }

  @Test
  void configurationSignatureChangesWithValueSettings() throws Exception {
    Field configField = AdaptConfig.class.getDeclaredField("config");
    configField.setAccessible(true);
    AdaptConfig previous = (AdaptConfig) configField.get(null);
    AdaptConfig config = new AdaptConfig();
    configField.set(null, config);
    try {
      Map<String, Double> multipliers = config.getValue().getValueMultipliers();
      multipliers.put("DIAMOND", 2D);
      String before = MaterialValue.currentConfigurationSignature();

      multipliers.put("DIAMOND", 3D);
      String after = MaterialValue.currentConfigurationSignature();

      assertThat(after).isNotEqualTo(before);
    } finally {
      configField.set(null, previous);
    }
  }
}
