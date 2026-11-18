package art.arcane.adapt.api.ability.internal;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class AbilityServiceListener implements Listener {
  private final List<BukkitAbilityProviderSource<?>> sources;
  private final Consumer<Class<?>> onUnregister;

  public AbilityServiceListener(List<BukkitAbilityProviderSource<?>> sources, Consumer<Class<?>> onUnregister) {
    this.sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
    this.onUnregister = Objects.requireNonNull(onUnregister, "onUnregister");
  }

  @EventHandler
  public void onServiceRegister(ServiceRegisterEvent event) {
    invalidate(event.getProvider(), false);
  }

  @EventHandler
  public void onServiceUnregister(ServiceUnregisterEvent event) {
    invalidate(event.getProvider(), true);
  }

  private void invalidate(RegisteredServiceProvider<?> registration, boolean unregistered) {
    if (registration == null) {
      return;
    }

    Class<?> serviceClass = registration.getService();

    for (int index = 0; index < sources.size(); index++) {
      BukkitAbilityProviderSource<?> source = sources.get(index);

      if (!source.service().equals(serviceClass)) {
        continue;
      }

      source.invalidate();

      if (unregistered) {
        onUnregister.accept(serviceClass);
      }
    }
  }
}
