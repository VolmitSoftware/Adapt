package art.arcane.adapt.api.value;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.value.Id;

import java.util.Objects;

/**
 * Config binding over the platform registries: block names read like Bukkit {@code Material.matchMaterial}, kept
 * only when they name a block, as the Bukkit constant name for vanilla blocks and the full id for modded ones.
 */
public final class RegistryConfigBinding implements AdaptConfig.Binding {
  private final RegistryLookup registries;

  public RegistryConfigBinding(RegistryLookup registries) {
    this.registries = Objects.requireNonNull(registries, "registries");
  }

  @Override
  public String blockName(String configured) {
    if (configured == null) {
      return null;
    }
    Id modded = MaterialNames.modded(configured);
    Id block = modded != null && registries.exists(RegistryKind.BLOCK, modded) ? modded : MaterialNames.vanilla(configured);
    if (block == null || !registries.exists(RegistryKind.BLOCK, block)) {
      return null;
    }
    return block.isMinecraft() ? block.legacyName() : block.toString();
  }

  @Override
  public void reloaded() {
  }
}
