package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Finds landings by following an airborne player tick by tick until they touch ground, coalescing the move events
 * that arrive meanwhile. The maps belong to the owning adaptation; the owner and landing callback are passed with each
 * move so the observation always answers to the adaptation that saw it.
 */
public final class KineticsLandingWatch {
  private final Map<UUID, Boolean> airborne;
  private final Map<UUID, Observation> pendingLandings;

  public KineticsLandingWatch(Map<UUID, Boolean> airborne, Map<UUID, Observation> pendingLandings) {
    this.airborne = airborne;
    this.pendingLandings = pendingLandings;
  }

  /** {@code onLanding} runs on the player's thread once the player lands while {@code owner} is still active. */
  public void observe(SimpleAdaptation<?> owner, PlayerMoveEvent e, BiConsumer<PlayerRef, PlayerMoveEvent> onLanding) {
    PlayerRef p = e.player();
    UUID playerId = p.uuid();
    if (e.isCancelled() || !owner.hasActiveAdaptation(p)) {
      forget(playerId);
      return;
    }
    if (!p.isOnGround()) {
      airborne.put(playerId, true);
    } else if (!Boolean.TRUE.equals(airborne.get(playerId))) {
      return;
    }
    Observation pending = pendingLandings.get(playerId);
    if (pending != null) {
      pending.event = e;
      return;
    }
    Observation observation = new Observation(owner, e, onLanding);
    pendingLandings.put(playerId, observation);
    schedule(p, observation);
  }

  public void forget(UUID playerId) {
    airborne.remove(playerId);
    pendingLandings.remove(playerId);
  }

  public void clear() {
    airborne.clear();
    pendingLandings.clear();
  }

  private void schedule(PlayerRef p, Observation observation) {
    if (!AdaptApi.scheduler().runEntity(p, () -> observeLanding(p, observation), 1)) {
      pendingLandings.remove(p.uuid(), observation);
      airborne.remove(p.uuid());
    }
  }

  private void observeLanding(PlayerRef p, Observation observation) {
    UUID playerId = p.uuid();
    if (pendingLandings.get(playerId) != observation) {
      return;
    }
    PlayerMoveEvent event = observation.event;
    SimpleAdaptation<?> owner = observation.owner;
    if (!owner.isRuntimeRegistered() || !p.isOnline() || !p.isValid() || p.isDead()
        || event.isCancelled() || !owner.hasActiveAdaptation(p)) {
      pendingLandings.remove(playerId, observation);
      airborne.remove(playerId);
      return;
    }
    boolean onGround = p.isOnGround();
    Boolean wasAirborne = airborne.put(playerId, !onGround);
    if (!onGround) {
      schedule(p, observation);
      return;
    }
    pendingLandings.remove(playerId, observation);
    if (!KineticsRubberSoul.isLanding(onGround, wasAirborne != null && wasAirborne)) {
      return;
    }
    observation.onLanding.accept(p, event);
  }

  public static final class Observation {
    private final SimpleAdaptation<?> owner;
    private final BiConsumer<PlayerRef, PlayerMoveEvent> onLanding;
    private PlayerMoveEvent event;

    private Observation(SimpleAdaptation<?> owner, PlayerMoveEvent event, BiConsumer<PlayerRef, PlayerMoveEvent> onLanding) {
      this.owner = owner;
      this.event = event;
      this.onLanding = onLanding;
    }
  }
}
