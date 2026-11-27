package art.arcane.adapt.client.server;

import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.asset.AdaptAssetCatalog;
import art.arcane.adapt.engine.asset.AssetCatalog;
import art.arcane.adapt.engine.asset.AssetInstaller;
import art.arcane.adapt.engine.asset.AssetHashes;
import art.arcane.adapt.engine.asset.AssetSkills;
import art.arcane.adapt.engine.asset.ThemeAssets;
import art.arcane.adapt.client.protocol.AssetRef;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.theme.ThemeKit;
import art.arcane.adapt.client.view.IconSprites;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What the asset sync offers native clients: every file of the catalog's bundles plus the generated theme, emblem and
 * trim of every registered skill without authored ones, each under the resource id a client loads it by. Built once per catalog
 * and skill registry revision and shared by every player.
 */
final class ClientAssets {
  private final AssetCatalog catalog;
  private final AssetInstaller store;
  private volatile Manifest cached;

  ClientAssets(AssetCatalog catalog, AssetInstaller store) {
    this.catalog = Objects.requireNonNull(catalog, "catalog");
    this.store = Objects.requireNonNull(store, "store");
  }

  /** One manifest: the frame that lists it and the bytes behind each listed resource. */
  record Manifest(long assetsRevision, long catalogRevision, ServerFrame.AssetManifest frame, Map<String, File> files) {
  }

  record File(String hash, byte[] bytes) {
  }

  Manifest manifest(SkillRegistry registry) {
    long assetsRevision = catalog.revision();
    long catalogRevision = registry.getCatalogRevision();
    Manifest known = cached;
    if (known != null && known.assetsRevision() == assetsRevision && known.catalogRevision() == catalogRevision) {
      return known;
    }
    Map<String, File> files = new LinkedHashMap<>();
    for (AdaptAssetCatalog.StoredFile file : store.files().values()) {
      files.put(file.resource(), new File(file.hash(), file.bytes()));
    }
    for (Id skill : AssetSkills.ids(registry)) {
      ThemeAssets theme = catalog.theme(skill).orElse(null);
      if (theme == null || !theme.generated()) {
        continue;
      }
      add(files, ThemeKit.dataId(skill.namespace(), skill.path()), theme.kitJson());
      add(files, ThemeKit.atlasId(skill.namespace(), skill.path()), theme.atlasPng());
    }
    for (Id skill : AssetSkills.ids(registry)) {
      addGenerated(files, IconSprites.emblem(skill), catalog.emblem(skill));
      addGenerated(files, IconSprites.trim(skill), catalog.trim(skill));
    }
    List<AssetRef> refs = new ArrayList<>(files.size());
    for (Map.Entry<String, File> file : files.entrySet()) {
      refs.add(new AssetRef(file.getKey(), file.getValue().hash()));
    }
    Manifest built = new Manifest(assetsRevision, catalogRevision, new ServerFrame.AssetManifest(refs),
        Collections.unmodifiableMap(files));
    cached = built;
    return built;
  }

  /** Art the catalog generated for a skill whose bundle has none; authored art is listed already. */
  private static void addGenerated(Map<String, File> files, String resource, Optional<byte[]> art) {
    if (!files.containsKey(resource) && art.isPresent()) {
      add(files, resource, art.get());
    }
  }

  private static void add(Map<String, File> files, String resource, byte[] bytes) {
    files.putIfAbsent(resource, new File(AssetHashes.sha1(bytes), bytes));
  }
}
