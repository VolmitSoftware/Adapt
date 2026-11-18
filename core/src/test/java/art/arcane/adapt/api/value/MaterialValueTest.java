package art.arcane.adapt.api.value;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.platform.value.Id;
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

    assertThat(MaterialValue.applyMultiplier(Id.minecraft("diamond"), 2D, multipliers)).isEqualTo(10D);
    assertThat(MaterialValue.applyMultiplier(Id.minecraft("stone"), 2D, multipliers)).isEqualTo(2D);
  }

  @Test
  void acceptsCaseInsensitiveMaterialKeys() {
    Map<String, Double> multipliers = Map.of("diamond", 3D);

    assertThat(MaterialValue.getMultiplier(Id.minecraft("diamond"), multipliers)).isEqualTo(3D);
  }

  @Test
  void convertsLoadedCacheDataToAConcurrentNullFreeMap() {
    Map<Id, Double> loaded = new HashMap<>();
    loaded.put(Id.minecraft("diamond"), 5D);
    loaded.put(Id.minecraft("stone"), null);

    Map<Id, Double> concurrent = MaterialValue.toConcurrentValueMap(loaded);

    assertThat(concurrent)
        .isInstanceOf(ConcurrentHashMap.class)
        .containsOnlyKeys(Id.minecraft("diamond"))
        .containsEntry(Id.minecraft("diamond"), 5D);
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
