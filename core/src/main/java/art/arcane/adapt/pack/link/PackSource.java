package art.arcane.adapt.pack.link;

import art.arcane.adapt.AdaptPorts;

import java.util.Optional;

/**
 * Where the built pack comes from. Installed through {@link AdaptPorts}; {@link #NONE} has no pack, and with no pack
 * nothing is offered.
 */
public interface PackSource {
  PackSource NONE = new PackSource() {
    @Override
    public Optional<PackArtifact> current() {
      return Optional.empty();
    }

    @Override
    public Runnable subscribe(Runnable changed) {
      return () -> { };
    }
  };

  static PackSource installed() {
    return AdaptPorts.get(PackSource.class, NONE);
  }

  Optional<PackArtifact> current();

  /** Calls {@code changed} on any thread after {@link #current()} changed; the returned action unsubscribes. */
  Runnable subscribe(Runnable changed);
}
