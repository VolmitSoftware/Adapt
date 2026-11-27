package art.arcane.adapt.engine.asset;

import art.arcane.adapt.api.asset.AssetEntry;

import java.util.List;
import java.util.Objects;

/**
 * One installed {@link art.arcane.adapt.api.asset.AssetBundle}: its namespace, the addon that installed it, the SHA-1
 * hex hash of its accepted entries and those entries, in the bundle's order. Entries that failed validation are not
 * listed.
 */
public record InstalledBundle(String namespace, String addon, String hash, List<AssetEntry> entries) {
  public InstalledBundle {
    Objects.requireNonNull(namespace, "namespace");
    Objects.requireNonNull(addon, "addon");
    Objects.requireNonNull(hash, "hash");
    entries = List.copyOf(entries);
  }
}
