package art.arcane.adapt.pack.manifest;

import art.arcane.adapt.AdaptPorts;

import java.util.Optional;

/**
 * The one source of the offered pack's manifest ({@code adapt_manifest.json} from the same zip), read once per pack.
 * Installed through {@link AdaptPorts} together with the pack source, so a manifest is present exactly while a pack is;
 * {@link #NONE} has none. The renderer, the HUD strip and pack toasts all read it here.
 */
public interface PackManifests {
  PackManifests NONE = new PackManifests() {
    @Override
    public Optional<PackManifest> current() {
      return Optional.empty();
    }

    @Override
    public Runnable subscribe(Runnable changed) {
      return () -> { };
    }
  };

  static PackManifests installed() {
    return AdaptPorts.get(PackManifests.class, NONE);
  }

  Optional<PackManifest> current();

  /** Calls {@code changed} on any thread after {@link #current()} changed; the returned action unsubscribes. */
  Runnable subscribe(Runnable changed);
}
