package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.viewer.ViewerGlowPort;
import art.arcane.adapt.content.adaptation.GlowLayers;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.viewer.GlowColor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

final class OverseerGlow {
  private static final GlowColor COLOR = GlowColor.RED;

  private final Map<UUID, Glow> glows = new ConcurrentHashMap<>();
  private final AtomicLong generations = new AtomicLong();

  void show(PlayerRef owner, LivingRef target, int ticks) {
    ViewerGlowPort coordinator = coordinator();
    if (coordinator == null || !coordinator.isAvailable() || ticks <= 0) {
      return;
    }
    UUID ownerId = owner.uuid();
    Glow glow = new Glow(owner, target.uuid(), target.entityId(), generations.incrementAndGet());
    Glow previous = glows.put(ownerId, glow);
    if (previous != null && (!previous.targetId().equals(glow.targetId()) || previous.runtimeEntityId() != glow.runtimeEntityId())) {
      unset(coordinator, previous);
    }
    if (!coordinator.set(GlowLayers.GOLEMANCY, target, owner, COLOR)) {
      glows.remove(ownerId, glow);
      return;
    }
    if (!AdaptApi.scheduler().runEntity(owner, () -> expire(glow), ticks)) {
      expire(glow);
    }
  }

  void clear(PlayerRef owner) {
    Glow glow = glows.remove(owner.uuid());
    ViewerGlowPort coordinator = coordinator();
    if (glow != null && coordinator != null) {
      unset(coordinator, glow);
    }
  }

  void clearAll() {
    List<Glow> current = List.copyOf(glows.values());
    glows.clear();
    ViewerGlowPort coordinator = coordinator();
    if (coordinator == null) {
      return;
    }
    for (Glow glow : current) {
      unset(coordinator, glow);
    }
  }

  private void expire(Glow glow) {
    ViewerGlowPort coordinator = coordinator();
    if (glows.remove(glow.owner().uuid(), glow) && coordinator != null) {
      unset(coordinator, glow);
    }
  }

  private static void unset(ViewerGlowPort coordinator, Glow glow) {
    coordinator.unset(GlowLayers.GOLEMANCY, glow.targetId(), glow.runtimeEntityId(), glow.owner());
  }

  private static ViewerGlowPort coordinator() {
    return ViewerGlowPort.current();
  }

  private record Glow(PlayerRef owner, UUID targetId, int runtimeEntityId, long generation) {
  }
}
