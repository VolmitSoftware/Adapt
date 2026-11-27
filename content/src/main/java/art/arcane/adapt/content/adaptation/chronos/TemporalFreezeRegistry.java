package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class TemporalFreezeRegistry {
  private static final ConcurrentMap<UUID, FrozenState> FROZEN = new ConcurrentHashMap<>();

  private TemporalFreezeRegistry() {
  }

  public static Lease acquire(EntityRef entity) {
    UUID leaseId = UUID.randomUUID();
    FrozenState state = FROZEN.compute(entity.uuid(), (id, existing) -> {
      FrozenState current = existing == null ? new FrozenState(capture(entity)) : existing;
      current.owners.add(leaseId);
      return current;
    });
    return new Lease(leaseId, state.snapshot);
  }

  public static boolean isFrozen(EntityRef entity) {
    return FROZEN.containsKey(entity.uuid());
  }

  public static Flight flightBeforeFreeze(PlayerRef player) {
    FrozenState state = FROZEN.get(player.uuid());
    return state == null ? new Flight(player.allowFlight(), player.isFlying()) : state.snapshot.flight();
  }

  public static void release(EntityRef entity, Lease lease, Vec3 velocity) {
    FrozenState[] released = new FrozenState[1];
    FROZEN.computeIfPresent(entity.uuid(), (id, state) -> {
      if (!state.owners.remove(lease.id())) {
        return state;
      }
      if (velocity != null) {
        state.releaseVelocity = velocity;
      }
      if (!state.owners.isEmpty()) {
        return state;
      }
      released[0] = state;
      return null;
    });
    FrozenState state = released[0];
    if (state == null) {
      return;
    }
    Snapshot snapshot = state.snapshot;
    entity.setGravity(snapshot.gravity());
    entity.setPersistent(snapshot.persistent());
    if (entity instanceof PlayerRef player) {
      Flight flight = snapshot.flight();
      if (flight.allowFlight()) {
        player.setAllowFlight(true);
        player.setFlying(flight.flying());
      } else {
        player.setFlying(false);
        player.setAllowFlight(false);
      }
    } else {
      entity.setVelocity(state.releaseVelocity == null ? snapshot.velocity() : state.releaseVelocity);
      if (entity instanceof LivingRef living && snapshot.hadAi() != null) {
        living.setAi(snapshot.hadAi());
      }
    }
  }

  public static void forget(UUID entityId) {
    FROZEN.remove(entityId);
  }

  private static Snapshot capture(EntityRef entity) {
    Flight flight = entity instanceof PlayerRef player
        ? new Flight(player.allowFlight(), player.isFlying()) : null;
    Vec3 velocity = entity instanceof PlayerRef ? Vec3.ZERO : entity.velocity();
    Boolean hadAi = entity instanceof LivingRef living && !(entity instanceof PlayerRef) ? living.hasAi() : null;
    return new Snapshot(velocity, entity.hasGravity(), entity.isPersistent(), flight, hadAi);
  }

  public record Flight(boolean allowFlight, boolean flying) {
  }

  public record Lease(UUID id, Snapshot snapshot) {
  }

  public record Snapshot(Vec3 velocity, boolean gravity, boolean persistent, Flight flight, Boolean hadAi) {
  }

  private static final class FrozenState {
    private final Snapshot snapshot;
    private final Set<UUID> owners = new HashSet<>();
    private Vec3 releaseVelocity;

    private FrozenState(Snapshot snapshot) {
      this.snapshot = snapshot;
    }
  }
}
