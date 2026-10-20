package art.arcane.adapt.api.insight;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public final class EntityInsight {
  private static final CopyOnWriteArrayList<Provider> PROVIDERS = new CopyOnWriteArrayList<>();
  private static final AtomicReference<Display> DISPLAY = new AtomicReference<>();

  private EntityInsight() {
  }

  public static void register(Provider provider) {
    PROVIDERS.addIfAbsent(Objects.requireNonNull(provider));
  }

  public static void unregister(Provider provider) {
    PROVIDERS.remove(provider);
  }

  public static List<String> details(Player viewer, LivingEntity target) {
    if (PROVIDERS.isEmpty()) {
      return List.of();
    }
    List<String> lines = new ArrayList<>();
    for (Provider provider : PROVIDERS) {
      lines.addAll(provider.details(viewer, target));
    }
    return lines;
  }

  public static void attach(Display display) {
    DISPLAY.set(Objects.requireNonNull(display));
  }

  public static void detach(Display display) {
    DISPLAY.compareAndSet(display, null);
  }

  public static boolean showing(UUID viewerId) {
    Display display = DISPLAY.get();
    return display != null && display.showing(viewerId);
  }

  @FunctionalInterface
  public interface Provider {
    List<String> details(Player viewer, LivingEntity target);
  }

  public interface Display {
    boolean showing(UUID viewerId);
  }
}
