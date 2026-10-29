package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.claims.ClaimsService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class TemporalFreezeRegistry {
  private static final ConcurrentMap<UUID, FrozenState> FROZEN = new ConcurrentHashMap<>();

  private TemporalFreezeRegistry() {
  }

  public static Lease acquire(Entity entity) {
    UUID leaseId = UUID.randomUUID();
    FrozenState state = FROZEN.compute(entity.getUniqueId(), (id, existing) -> {
      FrozenState current = existing == null ? new FrozenState(capture(entity)) : existing;
      current.owners.add(leaseId);
      return current;
    });
    return new Lease(leaseId, state.snapshot);
  }

  public static boolean isFrozen(Entity entity) {
    return FROZEN.containsKey(entity.getUniqueId());
  }

  public static Flight flightBeforeFreeze(Player player) {
    FrozenState state = FROZEN.get(player.getUniqueId());
    return state == null ? new Flight(player.getAllowFlight(), player.isFlying()) : state.snapshot.flight();
  }

  public static void release(Entity entity, Lease lease, Vector velocity) {
    FrozenState[] released = new FrozenState[1];
    FROZEN.computeIfPresent(entity.getUniqueId(), (id, state) -> {
      if (!state.owners.remove(lease.id())) {
        return state;
      }
      if (velocity != null) {
        state.releaseVelocity = velocity.clone();
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
    if (entity instanceof Player player) {
      Flight flight = snapshot.flight();
      if (flight.allowFlight()) {
        player.setAllowFlight(true);
        player.setFlying(flight.flying() && ClaimsService.permitsTeleport(player, player.getLocation()));
      } else {
        player.setFlying(false);
        player.setAllowFlight(false);
      }
    } else {
      entity.setVelocity(state.releaseVelocity == null ? snapshot.velocity() : state.releaseVelocity);
      if (entity instanceof LivingEntity living && snapshot.hadAi() != null) {
        living.setAI(snapshot.hadAi());
      }
    }
  }

  public static void forget(UUID entityId) {
    FROZEN.remove(entityId);
  }

  private static Snapshot capture(Entity entity) {
    Flight flight = entity instanceof Player player
        ? new Flight(player.getAllowFlight(), player.isFlying()) : null;
    Vector velocity = entity instanceof Player ? new Vector() : entity.getVelocity().clone();
    Boolean hadAi = entity instanceof LivingEntity living && !(entity instanceof Player) ? living.hasAI() : null;
    return new Snapshot(velocity, entity.hasGravity(), entity.isPersistent(), flight, hadAi);
  }

  public record Flight(boolean allowFlight, boolean flying) {
  }

  public record Lease(UUID id, Snapshot snapshot) {
  }

  public record Snapshot(Vector velocity, boolean gravity, boolean persistent, Flight flight, Boolean hadAi) {
    @Override
    public Vector velocity() {
      return velocity.clone();
    }
  }

  private static final class FrozenState {
    private final Snapshot snapshot;
    private final Set<UUID> owners = new HashSet<>();
    private Vector releaseVelocity;

    private FrozenState(Snapshot snapshot) {
      this.snapshot = snapshot;
    }
  }
}
