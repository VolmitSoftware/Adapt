package art.arcane.adapt.platform.event.server;

import art.arcane.adapt.platform.event.AdaptEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A Bukkit service provider was registered or unregistered. plugin() is the provider's owner, or null.
 * Posted only on Bukkit. Read-only.
 */
@ApiStatus.Experimental
public class ServiceChangeEvent extends AdaptEvent {
  private final Class<?> service;
  private final String plugin;
  private final boolean registered;

  public ServiceChangeEvent(Class<?> service, String plugin, boolean registered) {
    this.service = Objects.requireNonNull(service, "service");
    this.plugin = plugin;
    this.registered = registered;
  }

  /** The service type. */
  public Class<?> service() {
    return service;
  }

  /** Name of the plugin that owns the provider, or null. */
  public String plugin() {
    return plugin;
  }

  /** True for a registration, false for a removal. */
  public boolean registered() {
    return registered;
  }
}
