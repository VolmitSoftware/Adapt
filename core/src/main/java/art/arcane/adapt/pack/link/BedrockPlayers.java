package art.arcane.adapt.pack.link;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Detects Bedrock players without a compile-time dependency: Floodgate's API first, then Geyser's, both found by
 * reflection through the class loaders the platform supplies. While neither is loaded the probe is retried every few
 * seconds. Floodgate gives unlinked Bedrock players ids whose upper 64 bits are zero, which is checked either way.
 */
public final class BedrockPlayers implements Predicate<UUID> {
  static final String FLOODGATE_API = "org.geysermc.floodgate.api.FloodgateApi";
  static final String GEYSER_API = "org.geysermc.geyser.api.GeyserApi";
  private static final long RETRY_MILLIS = 5_000L;

  private final Supplier<List<ClassLoader>> loaders;
  private final LongSupplier clock;
  private volatile Probe probe;
  private volatile long nextProbeAt;

  public BedrockPlayers(Supplier<List<ClassLoader>> loaders) {
    this(loaders, System::currentTimeMillis);
  }

  BedrockPlayers(Supplier<List<ClassLoader>> loaders, LongSupplier clock) {
    this.loaders = Objects.requireNonNull(loaders, "loaders");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public boolean test(UUID playerId) {
    if (playerId == null) {
      return false;
    }
    if (playerId.getMostSignificantBits() == 0L) {
      return true;
    }
    Probe active = probe();
    return active != null && active.bedrock(playerId);
  }

  private Probe probe() {
    Probe active = probe;
    if (active != null) {
      return active;
    }
    long now = clock.getAsLong();
    if (now < nextProbeAt) {
      return null;
    }
    nextProbeAt = now + RETRY_MILLIS;
    for (ClassLoader loader : loaders.get()) {
      Probe found = find(loader, FLOODGATE_API, "getInstance", "isFloodgatePlayer");
      if (found == null) {
        found = find(loader, GEYSER_API, "api", "isBedrockPlayer");
      }
      if (found != null) {
        probe = found;
        return found;
      }
    }
    return null;
  }

  private static Probe find(ClassLoader loader, String api, String accessor, String check) {
    try {
      Class<?> type = Class.forName(api, true, loader);
      Object instance = type.getMethod(accessor).invoke(null);
      if (instance == null) {
        return null;
      }
      Method method = type.getMethod(check, UUID.class);
      return new Probe(instance, method);
    } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
      return null;
    }
  }

  private record Probe(Object instance, Method check) {
    private boolean bedrock(UUID playerId) {
      try {
        return Boolean.TRUE.equals(check.invoke(instance, playerId));
      } catch (IllegalAccessException | InvocationTargetException | RuntimeException failure) {
        return false;
      }
    }
  }
}
