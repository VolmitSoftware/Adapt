package art.arcane.adapt.platform.event.server;

import art.arcane.adapt.platform.event.AdaptEvent;

import java.util.Objects;

/**
 * A service provider was registered or unregistered. plugin() is the provider's owner, or null.
 * Read-only.
 */
public class ServiceChangeEvent extends AdaptEvent {
  private final Class<?> service;
  private final String plugin;
  private final boolean registered;

  public ServiceChangeEvent(Class<?> service, String plugin, boolean registered) {
    this.service = Objects.requireNonNull(service, "service");
    this.plugin = plugin;
    this.registered = registered;
  }

  public Class<?> service() {
    return service;
  }

  public String plugin() {
    return plugin;
  }

  public boolean registered() {
    return registered;
  }
}
