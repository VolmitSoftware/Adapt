package art.arcane.adapt.content.adaptation.enchanting;

import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.catalog.EnchantingMessages;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Material;

import java.util.List;

final class EnchantingPreferences {
  private EnchantingPreferences() {
  }

  static PlayerPreference<Reserve> reserve(String id, TextKey label) {
    return new PlayerPreference<>(Reserve.class, new PlayerPreference.Definition<>(id, label, Reserve.NONE, List.of(
        new PlayerPreference.Choice<>(Reserve.NONE, EnchantingMessages.RESERVE_NONE, Material.GLASS_BOTTLE, 1),
        new PlayerPreference.Choice<>(Reserve.FIVE, EnchantingMessages.RESERVE_FIVE, Material.EXPERIENCE_BOTTLE, 1),
        new PlayerPreference.Choice<>(Reserve.TEN, EnchantingMessages.RESERVE_TEN, Material.LAPIS_LAZULI, 1))));
  }

  enum Reserve {
    NONE(0), FIVE(5), TEN(10);

    private final int amount;

    Reserve(int amount) {
      this.amount = amount;
    }

    int amount() {
      return amount;
    }
  }
}
