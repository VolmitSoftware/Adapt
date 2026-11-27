package art.arcane.adapt.api.service;

import org.jetbrains.annotations.ApiStatus;

/** A service provided through {@link ServiceRegistry#provide}; keep it to withdraw the service. */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface ServiceRegistration {
  /** Removes the service. An earlier provider of the same type becomes current again. Safe to call twice. */
  void withdraw();
}
