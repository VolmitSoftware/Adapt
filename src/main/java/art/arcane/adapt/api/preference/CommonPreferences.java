package art.arcane.adapt.api.preference;

import art.arcane.adapt.localization.catalog.PreferenceMessages;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Material;

import java.util.List;

public final class CommonPreferences {
  public static final PlayerPreference<Toggle> ENABLED = toggle("enabled", PreferenceMessages.ENABLED);
  public static final PlayerPreference<Toggle> SKILL_ENABLED = toggle("enabled", PreferenceMessages.SKILL_ENABLED);

  private CommonPreferences() {
  }

  public static PlayerPreference<Toggle> toggle(String id, TextKey label) {
    return toggle(id, label, Toggle.ON);
  }

  public static PlayerPreference<Toggle> toggle(String id, TextKey label, Toggle defaultValue) {
    return new PlayerPreference<>(Toggle.class, new PlayerPreference.Definition<>(id, label, defaultValue, List.of(
        new PlayerPreference.Choice<>(Toggle.ON, PreferenceMessages.ON, Material.LIME_STAINED_GLASS_PANE, 1),
        new PlayerPreference.Choice<>(Toggle.OFF, PreferenceMessages.OFF, Material.RED_STAINED_GLASS_PANE, 1))));
  }

  public static PlayerPreference<Scale> scale(String id, TextKey label) {
    return new PlayerPreference<>(Scale.class, new PlayerPreference.Definition<>(id, label, Scale.FULL, List.of(
        new PlayerPreference.Choice<>(Scale.FULL, PreferenceMessages.FULL, Material.LIME_STAINED_GLASS_PANE, 1),
        new PlayerPreference.Choice<>(Scale.HALF, PreferenceMessages.HALF, Material.YELLOW_STAINED_GLASS_PANE, 1),
        new PlayerPreference.Choice<>(Scale.QUARTER, PreferenceMessages.QUARTER, Material.ORANGE_STAINED_GLASS_PANE, 1))));
  }

  public enum Toggle {
    ON, OFF
  }

  public enum Scale {
    FULL(1D), HALF(0.5D), QUARTER(0.25D);

    private final double multiplier;

    Scale(double multiplier) {
      this.multiplier = multiplier;
    }

    public double multiplier() {
      return multiplier;
    }
  }
}
