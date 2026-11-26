package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Night Vision kept up while underwater and removed again on surfacing, touching only an effect this helper applied:
 * a potion's Night Vision, or one with a higher amplifier or visible particles, is left alone.
 */
final class SeaborneNightSight {
  static final int DURATION_TICKS = 600;
  static final int REFRESH_THRESHOLD_TICKS = 500;
  private static final Id NIGHT_VISION = Id.minecraft("night_vision");

  private final Set<UUID> applied = ConcurrentHashMap.newKeySet();
  private final Map<UUID, Boolean> mutating = new ConcurrentHashMap<>();

  void refresh(PlayerRef player) {
    UUID id = player.uuid();
    EffectInstance current = player.effect(NIGHT_VISION);
    if (!applied.contains(id) || current == null) {
      applied.remove(id);
      if (current == null && add(player)) {
        applied.add(id);
      }
      return;
    }
    if (!isManaged(current)) {
      applied.remove(id);
      return;
    }
    if (current.durationTicks() <= REFRESH_THRESHOLD_TICKS && !add(player)) {
      applied.remove(id);
    }
  }

  void clear(PlayerRef player) {
    UUID id = player.uuid();
    boolean owned = applied.remove(id);
    EffectInstance current = player.effect(NIGHT_VISION);
    if (!mayRemove(owned, current)) {
      return;
    }
    mutating.put(id, true);
    try {
      player.removeEffect(NIGHT_VISION);
    } finally {
      mutating.remove(id);
    }
  }

  /** A Night Vision change this helper did not make means the effect is no longer its own. */
  void changed(PlayerRef player, Id effect) {
    if (NIGHT_VISION.equals(effect) && !mutating.containsKey(player.uuid())) {
      applied.remove(player.uuid());
    }
  }

  boolean holds(UUID id) {
    return applied.contains(id);
  }

  Set<UUID> holders() {
    return Set.copyOf(applied);
  }

  void forget(UUID id) {
    applied.remove(id);
  }

  private boolean add(PlayerRef player) {
    mutating.put(player.uuid(), true);
    try {
      return player.addEffect(new EffectInstance(NIGHT_VISION, DURATION_TICKS, 0, false, false, false));
    } finally {
      mutating.remove(player.uuid());
    }
  }

  static boolean mayRemove(boolean owned, EffectInstance current) {
    return owned && current != null && isManaged(current);
  }

  static boolean isManaged(EffectInstance effect) {
    return effect.amplifier() == 0 && !effect.ambient() && !effect.particles();
  }
}
