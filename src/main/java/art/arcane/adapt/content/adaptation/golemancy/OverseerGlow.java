package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.fx.ViewerGlowCoordinator;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

final class OverseerGlow {
  private static final ChatColor COLOR = ChatColor.RED;

  private final Map<UUID, Glow> glows = new ConcurrentHashMap<>();
  private final AtomicLong generations = new AtomicLong();

  void show(Player owner, LivingEntity target, int ticks) {
    ViewerGlowCoordinator coordinator = coordinator();
    if (coordinator == null || !coordinator.isAvailable() || ticks <= 0) {
      return;
    }
    UUID ownerId = owner.getUniqueId();
    Glow glow = new Glow(owner, target.getUniqueId(), target.getEntityId(), generations.incrementAndGet());
    Glow previous = glows.put(ownerId, glow);
    if (previous != null && (!previous.targetId().equals(glow.targetId()) || previous.runtimeEntityId() != glow.runtimeEntityId())) {
      unset(coordinator, previous);
    }
    if (!coordinator.set(ViewerGlowCoordinator.Layer.GOLEMANCY, target, owner, COLOR)) {
      glows.remove(ownerId, glow);
      return;
    }
    if (!J.runEntity(owner, () -> expire(glow), ticks)) {
      expire(glow);
    }
  }

  void clear(Player owner) {
    Glow glow = glows.remove(owner.getUniqueId());
    ViewerGlowCoordinator coordinator = coordinator();
    if (glow != null && coordinator != null) {
      unset(coordinator, glow);
    }
  }

  void clearAll() {
    List<Glow> current = List.copyOf(glows.values());
    glows.clear();
    ViewerGlowCoordinator coordinator = coordinator();
    if (coordinator == null) {
      return;
    }
    for (Glow glow : current) {
      unset(coordinator, glow);
    }
  }

  private void expire(Glow glow) {
    ViewerGlowCoordinator coordinator = coordinator();
    if (glows.remove(glow.owner().getUniqueId(), glow) && coordinator != null) {
      unset(coordinator, glow);
    }
  }

  private static void unset(ViewerGlowCoordinator coordinator, Glow glow) {
    coordinator.unset(ViewerGlowCoordinator.Layer.GOLEMANCY, glow.targetId(), glow.runtimeEntityId(), glow.owner());
  }

  private static ViewerGlowCoordinator coordinator() {
    Adapt plugin = Adapt.instance;
    return plugin == null ? null : plugin.getViewerGlowCoordinator();
  }

  private record Glow(Player owner, UUID targetId, int runtimeEntityId, long generation) {
  }
}
