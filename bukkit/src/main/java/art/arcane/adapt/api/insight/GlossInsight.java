package art.arcane.adapt.api.insight;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class GlossInsight {
  public static final String API_CLASS = "art.arcane.gloss.api.GlossAPI";
  private static final long DISCOVERY_RETRY_MILLIS = 5000L;
  private static final int SINGLE_STACK = 1;

  private final Plugin owner;
  private final Access access;
  private final Set<String> reportedFailures = ConcurrentHashMap.newKeySet();
  private final Set<Class<?>> unsupportedServices = Collections.newSetFromMap(new WeakHashMap<>());
  private volatile Binding binding;
  private volatile long nextDiscoveryAt;
  private volatile boolean restrictionManaged;
  private volatile boolean restricted;

  public GlossInsight(Plugin owner) {
    this(owner, new Access(Bukkit::getServicesManager, System::currentTimeMillis));
  }

  GlossInsight(Plugin owner, Access access) {
    this.owner = Objects.requireNonNull(owner);
    this.access = Objects.requireNonNull(access);
  }

  public boolean available() {
    return resolveBinding() != null;
  }

  public boolean overlayActive(LivingEntity probe) {
    Binding active = resolveBinding();
    if (active == null) {
      return false;
    }
    try {
      return Boolean.TRUE.equals(active.overlay().invoke(active.provider(), probe, SINGLE_STACK));
    } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
      reportFailure(active, exception);
      return false;
    }
  }

  public boolean update(Player viewer, LivingEntity target, List<String> details, long durationMillis) {
    Binding active = resolveBinding();
    if (active == null) {
      return false;
    }
    try {
      return Boolean.TRUE.equals(active.update().invoke(active.provider(), owner, viewer, target,
          details, durationMillis));
    } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
      reportFailure(active, exception);
      return false;
    }
  }

  public void clear(UUID viewerId) {
    Binding active = resolveBinding();
    if (active == null) {
      return;
    }
    try {
      active.clear().invoke(active.provider(), owner, viewerId);
    } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
      reportFailure(active, exception);
    }
  }

  public synchronized void setRestricted(boolean value) {
    restrictionManaged = true;
    restricted = value;
    Binding active = resolveBinding();
    if (active != null) {
      applyRestriction(active);
    }
  }

  public synchronized void refresh() {
    binding = null;
    nextDiscoveryAt = 0L;
    resolveBinding();
  }

  private Binding resolveBinding() {
    Binding active = binding;
    if (active != null && active.plugin().isEnabled()) {
      return active;
    }
    long now = access.clock().getAsLong();
    if (now < nextDiscoveryAt) {
      return null;
    }
    synchronized (this) {
      active = binding;
      if (active != null && active.plugin().isEnabled()) {
        return active;
      }
      if (now < nextDiscoveryAt) {
        return null;
      }
      nextDiscoveryAt = now + DISCOVERY_RETRY_MILLIS;
      Binding discovered = discover();
      binding = discovered;
      if (discovered != null) {
        applyRestriction(discovered);
      }
      return binding;
    }
  }

  private Binding discover() {
    ServicesManager services = access.services().get();
    if (services == null) {
      return null;
    }
    for (Class<?> service : services.getKnownServices()) {
      if (!API_CLASS.equals(service.getName()) || unsupportedServices.contains(service)) {
        continue;
      }
      for (RegisteredServiceProvider<?> registration : services.getRegistrations(service)) {
        Plugin plugin = registration.getPlugin();
        Object provider = registration.getProvider();
        if (plugin == null || !plugin.isEnabled() || provider == null) {
          continue;
        }
        try {
          return new Binding(plugin, provider,
              service.getMethod("refreshEntityOverlay", LivingEntity.class, int.class),
              service.getMethod("updateEntityInsight", Plugin.class, Player.class, LivingEntity.class,
                  List.class, long.class),
              service.getMethod("clearEntityInsight", Plugin.class, UUID.class),
              service.getMethod("restrictEntityOverlays", Plugin.class, boolean.class));
        } catch (NoSuchMethodException exception) {
          unsupportedServices.add(service);
          owner.getLogger().log(Level.WARNING,
              "Loaded " + plugin.getName() + " " + plugin.getDescription().getVersion()
                  + " does not provide the entity-overlay API required by Discovery Insight and golem names."
                  + " Install the current Gloss build and restart the server."
                  + " The Gloss entity overlay stays unavailable to Adapt for this loaded Gloss API.", exception);
          break;
        }
      }
    }
    return null;
  }

  private void applyRestriction(Binding active) {
    if (!restrictionManaged) {
      return;
    }
    try {
      active.restrict().invoke(active.provider(), owner, restricted);
    } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
      reportFailure(active, exception);
    }
  }

  private synchronized void reportFailure(Binding failed, Throwable exception) {
    if (binding == failed) {
      binding = null;
      nextDiscoveryAt = access.clock().getAsLong() + DISCOVERY_RETRY_MILLIS;
    }
    Throwable failure = exception instanceof InvocationTargetException invocation && invocation.getCause() != null
        ? invocation.getCause() : exception;
    String key = failure.getClass().getName() + ":" + failure.getMessage();
    if (reportedFailures.add(key)) {
      owner.getLogger().log(Level.WARNING, "Could not update the Gloss entity overlay. The integration will retry.", failure);
    }
  }

  record Access(Supplier<ServicesManager> services, LongSupplier clock) {
    Access {
      Objects.requireNonNull(services);
      Objects.requireNonNull(clock);
    }
  }

  private record Binding(Plugin plugin, Object provider, Method overlay, Method update, Method clear, Method restrict) {
  }
}
