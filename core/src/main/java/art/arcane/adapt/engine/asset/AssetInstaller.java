package art.arcane.adapt.engine.asset;

import art.arcane.adapt.api.asset.AssetBundle;

import java.util.Map;

/** The engine's write side of the {@link AssetCatalog}: what the addon lifecycle installs and the asset sync serves. */
public interface AssetInstaller {
  /**
   * Installs {@code bundle} for {@code addon}, replacing an installed bundle of the same namespace in its place. An entry
   * that fails validation is skipped with one logged error. Returns what was accepted.
   */
  InstalledBundle install(AssetBundle bundle, String addon);

  /** Removes the bundle of {@code namespace}; false when none was installed. */
  boolean uninstall(String namespace);

  /**
   * The registered skills changed. Raises the revision and tells listeners when that changed the themes: a skill
   * came or went, or the colour or icon a generated theme is drawn from moved.
   */
  void skillsChanged();

  /** Every accepted file by the resource id clients load it by, in bundle order. */
  Map<String, AdaptAssetCatalog.StoredFile> files();
}
