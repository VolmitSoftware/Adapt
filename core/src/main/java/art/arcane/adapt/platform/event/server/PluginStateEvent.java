package art.arcane.adapt.platform.event.server;

import art.arcane.adapt.platform.event.AdaptEvent;

import java.util.Objects;

/**
 * A plugin or mod was enabled or is being disabled. Modded platforms post enabled events for loaded
 * mods once at server start. Read-only.
 */
public class PluginStateEvent extends AdaptEvent {
  private final String plugin;
  private final boolean enabled;

  public PluginStateEvent(String plugin, boolean enabled) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.enabled = enabled;
  }

  public String plugin() {
    return plugin;
  }

  public boolean enabled() {
    return enabled;
  }
}
