package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.addon.AddonHandle;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.engine.bridge.HostKeys;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.function.Function;

/** Uninstalls the addons a plugin provided when that plugin is disabled. */
public final class AddonProviders implements Listener {
  private final Function<Class<?>, Plugin> providers;

  public AddonProviders() {
    this(AddonProviders::providingPlugin);
  }

  AddonProviders(Function<Class<?>, Plugin> providers) {
    this.providers = Objects.requireNonNull(providers, "providers");
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PluginDisableEvent event) {
    Plugin disabled = event.getPlugin();
    for (AddonHandle handle : Engine.addons().snapshot(HostKeys.key())) {
      if (Id.ADAPT.equals(handle.namespace()) || providers.apply(handle.addon().getClass()) != disabled) {
        continue;
      }
      AdaptLog.info("Removing addon " + handle.namespace() + ": its plugin " + disabled.getName() + " was disabled.");
      handle.uninstall();
    }
  }

  private static Plugin providingPlugin(Class<?> type) {
    try {
      return JavaPlugin.getProvidingPlugin(type);
    } catch (IllegalArgumentException | IllegalStateException notAPlugin) {
      return null;
    }
  }
}
