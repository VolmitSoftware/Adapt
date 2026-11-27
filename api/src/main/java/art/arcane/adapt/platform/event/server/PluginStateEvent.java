package art.arcane.adapt.platform.event.server;

import art.arcane.adapt.platform.event.AdaptEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A plugin was enabled or is being disabled. Modded platforms post it only for Adapt itself: enabled once the server
 * has started, disabled when it begins stopping. Read-only.
 */
@ApiStatus.Experimental
public class PluginStateEvent extends AdaptEvent {
  private final String plugin;
  private final boolean enabled;

  public PluginStateEvent(String plugin, boolean enabled) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.enabled = enabled;
  }

  /** Name of the plugin; Adapt's own name on modded platforms. */
  public String plugin() {
    return plugin;
  }

  /** True when the plugin was enabled, false when it is being disabled. */
  public boolean enabled() {
    return enabled;
  }
}
