package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.api.golem.GolemRuneType;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public record Infusion(String id, NamespacedKey result, Supplier<ItemStack> resultItem, InfusionIngredient center,
                       List<InfusionIngredient> pedestals, int essenceLevels, int durationTicks, String unlockAdaptation, int unlockLevel) {
  static final String RUNE_PREFIX = "rune/";

  public Infusion {
    Objects.requireNonNull(id);
    Objects.requireNonNull(result);
    Objects.requireNonNull(resultItem);
    Objects.requireNonNull(center);
    pedestals = List.copyOf(pedestals);
    Objects.requireNonNull(unlockAdaptation);
  }

  public int runeTier() {
    String key = result.getKey();
    int slash = key.lastIndexOf('/');
    if (!key.startsWith(RUNE_PREFIX) || slash != key.length() - 2) {
      return 0;
    }
    int tier = key.charAt(slash + 1) - '0';
    return tier >= 1 && tier <= 9 ? tier : 0;
  }

  public Optional<GolemRuneType> runeAspect() {
    if (runeTier() == 0) {
      return Optional.empty();
    }
    String key = result.getKey();
    return GolemRuneType.fromId(key.substring(RUNE_PREFIX.length(), key.length() - 2));
  }
}
