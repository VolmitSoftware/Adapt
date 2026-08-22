package art.arcane.adapt.api.protection;

import art.arcane.adapt.AdaptConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class ProtectorRegistryTest {
  @Test
  void refreshesDefaultProtectorSelectionAfterConfigurationChanges() throws Exception {
    Field configField = AdaptConfig.class.getDeclaredField("config");
    configField.setAccessible(true);
    AdaptConfig previous = (AdaptConfig) configField.get(null);
    configField.set(null, new AdaptConfig());
    try {
      ProtectorRegistry registry = new ProtectorRegistry();
      MutableProtector protector = new MutableProtector(false);
      registry.registerProtector(protector);

      assertThat(registry.getAllProtectors()).containsExactly(protector);
      assertThat(registry.getDefaultProtectors()).isEmpty();

      protector.setEnabledByDefault(true);
      registry.refreshDefaultProtectors();

      assertThat(registry.getDefaultProtectors()).containsExactly(protector);
    } finally {
      configField.set(null, previous);
    }
  }

  private static final class MutableProtector implements Protector {
    private boolean enabledByDefault;

    private MutableProtector(boolean enabledByDefault) {
      this.enabledByDefault = enabledByDefault;
    }

    @Override
    public String getName() {
      return "mutable";
    }

    @Override
    public boolean isEnabledByDefault() {
      return enabledByDefault;
    }

    private void setEnabledByDefault(boolean enabledByDefault) {
      this.enabledByDefault = enabledByDefault;
    }
  }
}
