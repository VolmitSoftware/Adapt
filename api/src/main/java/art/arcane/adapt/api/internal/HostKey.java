package art.arcane.adapt.api.internal;

import org.jetbrains.annotations.ApiStatus;

/**
 * Proof that the caller is the platform host running Adapt. {@link Engine#claimHost()} creates the only instance and
 * hands it out once; every internal member that starts or stops a core, or reads or changes what other parties
 * registered, takes it.
 */
@ApiStatus.Internal
public final class HostKey {
  private final Class<?> claimant;

  HostKey(Class<?> claimant) {
    this.claimant = claimant;
  }

  Class<?> claimant() {
    return claimant;
  }
}
