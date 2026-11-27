package art.arcane.adapt.pack;

import art.arcane.adapt.pack.layout.PackLayoutReader;
import art.arcane.adapt.pack.manifest.PackManifest;
import art.arcane.adapt.pack.manifest.PackManifests;
import art.arcane.adapt.pack.text.PackFontMetrics;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * The renderer's resources for the pack the server delivers: the published manifest from {@link PackManifests}, the
 * built-in layout and the vanilla font metrics. Rebuilt only when the published manifest changes; null while there is none.
 */
public final class DeliveredResources implements Supplier<PackResources> {
  private final Supplier<Optional<PackManifest>> manifests;
  private PackManifest loadedFrom;
  private PackResources loaded;

  public DeliveredResources() {
    this(() -> PackManifests.installed().current());
  }

  public DeliveredResources(Supplier<Optional<PackManifest>> manifests) {
    this.manifests = manifests;
  }

  @Override
  public synchronized PackResources get() {
    PackManifest manifest = manifests.get().orElse(null);
    if (manifest == null) {
      loadedFrom = null;
      loaded = null;
      return null;
    }
    if (manifest != loadedFrom) {
      loaded = new PackResources(manifest, PackLayoutReader.builtIn(), PackFontMetrics.vanilla());
      loadedFrom = manifest;
    }
    return loaded;
  }
}
