package art.arcane.adapt.content.adaptation.enchanting;

import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.value.Id;

import java.util.List;

final class EnchantingPreferences {
  private EnchantingPreferences() {
  }

  static PlayerPreference<Reserve> reserve(String id, Text label) {
    return PlayerPreference.of(id, Reserve.class, Reserve.NONE, List.of(
        new PlayerPreference.Choice<>(Reserve.NONE, Text.key("adapt.enchanting.preferences.reserve_none"), Id.minecraft("glass_bottle"), 1),
        new PlayerPreference.Choice<>(Reserve.FIVE, Text.key("adapt.enchanting.preferences.reserve_five"), Id.minecraft("experience_bottle"), 1),
        new PlayerPreference.Choice<>(Reserve.TEN, Text.key("adapt.enchanting.preferences.reserve_ten"), Id.minecraft("lapis_lazuli"), 1))).labelled(label);
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
