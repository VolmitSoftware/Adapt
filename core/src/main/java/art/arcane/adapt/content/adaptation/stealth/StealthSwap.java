package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decoy Swap's place swap: the decoy moves to the player, then the player to where the decoy stood, and the decoy is
 * moved back if the player's leg fails.
 */
public final class StealthSwap {
  private final SimpleAdaptation<?> owner;
  private final String name;
  private final Swapped onSwapped;
  private final Set<UUID> swapsInFlight = ConcurrentHashMap.newKeySet();

  public StealthSwap(SimpleAdaptation<?> owner, String name, Swapped onSwapped) {
    this.owner = owner;
    this.name = name;
    this.onSwapped = onSwapped;
  }

  /** Swaps {@code p} with {@code anchor} when it is within {@code range}; {@code onSwapped} runs once both legs land. */
  public void swap(PlayerRef p, EntityRef anchor, double range) {
    UUID id = p.uuid();
    if (!swapsInFlight.add(id)) {
      return;
    }

    Loc playerLoc = p.location();
    if (!Tasks.runEntity(anchor, () -> {
      if (!anchor.isValid()) {
        swapsInFlight.remove(id);
        return;
      }

      Loc anchorLoc = anchor.location();
      if (!anchorLoc.sameWorld(playerLoc)) {
        swapsInFlight.remove(id);
        return;
      }

      double dx = anchorLoc.x() - playerLoc.x();
      double dy = anchorLoc.y() - playerLoc.y();
      double dz = anchorLoc.z() - playerLoc.z();
      if ((dx * dx) + (dy * dy) + (dz * dz) > range * range) {
        swapsInFlight.remove(id);
        return;
      }

      Loc playerDest = anchorLoc.withRotation(playerLoc.yaw(), playerLoc.pitch());
      startAnchorTeleport(p, anchor, anchorLoc, playerLoc, playerDest, id);
    })) {
      swapsInFlight.remove(id);
    }
  }

  public void clear() {
    swapsInFlight.clear();
  }

  /** Runs on the player's thread once both legs landed: where the decoy stood, and where the player now stands. */
  public interface Swapped {
    void swapped(PlayerRef player, Loc anchorOrigin, Loc playerDestination);
  }

  private void startAnchorTeleport(PlayerRef p, EntityRef anchor, Loc anchorOrigin, Loc playerOrigin,
                                   Loc playerDestination, UUID playerId) {
    CompletableFuture<Boolean> teleport = beginTeleport(anchor, playerOrigin, "decoy");
    if (teleport == null) {
      swapsInFlight.remove(playerId);
      return;
    }
    teleport.whenComplete((success, failure) -> {
      reportTeleportFailure(anchor, "decoy", failure);
      if (!teleportCompleted(success, failure)) {
        swapsInFlight.remove(playerId);
        return;
      }
      if (!canStartPlayerLeg(swapsInFlight.contains(playerId), owner.isRuntimeRegistered())) {
        rollbackAnchor(anchor, anchorOrigin, playerId);
        return;
      }
      if (!Tasks.runEntity(p, () -> startPlayerTeleport(
          p,
          anchor,
          anchorOrigin,
          playerDestination,
          playerId
      ))) {
        rollbackAnchor(anchor, anchorOrigin, playerId);
      }
    });
  }

  private void startPlayerTeleport(PlayerRef p, EntityRef anchor, Loc anchorOrigin,
                                   Loc playerDestination, UUID playerId) {
    if (!canStartPlayerLeg(swapsInFlight.contains(playerId), owner.isRuntimeRegistered()) || !p.isOnline()) {
      rollbackAnchor(anchor, anchorOrigin, playerId);
      return;
    }
    CompletableFuture<Boolean> teleport = beginTeleport(p, playerDestination, "player");
    if (teleport == null) {
      rollbackAnchor(anchor, anchorOrigin, playerId);
      return;
    }
    teleport.whenComplete((success, failure) -> {
      reportTeleportFailure(p, "player", failure);
      if (!teleportCompleted(success, failure)) {
        rollbackAnchor(anchor, anchorOrigin, playerId);
        return;
      }
      if (!Tasks.runEntity(p, () -> commitSwap(p, anchorOrigin, playerDestination, playerId))) {
        swapsInFlight.remove(playerId);
        AdaptLog.warn(name + " completed both teleports but could not schedule rewards for "
            + playerId + ".");
      }
    });
  }

  private void commitSwap(PlayerRef p, Loc anchorOrigin, Loc playerDestination, UUID playerId) {
    if (!p.isOnline() || !owner.isRuntimeRegistered()) {
      swapsInFlight.remove(playerId);
      return;
    }
    swapsInFlight.remove(playerId);
    onSwapped.swapped(p, anchorOrigin, playerDestination);
  }

  private void rollbackAnchor(EntityRef anchor, Loc anchorOrigin, UUID playerId) {
    if (!Tasks.runEntity(anchor, () -> {
      if (!anchor.isValid()) {
        swapsInFlight.remove(playerId);
        AdaptLog.warn(name + " could not roll back invalid decoy "
            + anchor.uuid() + ".");
        return;
      }
      CompletableFuture<Boolean> rollback = beginTeleport(anchor, anchorOrigin, "decoy rollback");
      if (rollback == null) {
        swapsInFlight.remove(playerId);
        return;
      }
      rollback.whenComplete((success, failure) -> {
        reportTeleportFailure(anchor, "decoy rollback", failure);
        if (!teleportCompleted(success, failure)) {
          AdaptLog.warn(name + " could not roll back decoy "
              + anchor.uuid() + " to " + anchorOrigin + ".");
        }
        swapsInFlight.remove(playerId);
      });
    })) {
      swapsInFlight.remove(playerId);
      AdaptLog.warn(name + " could not schedule rollback for decoy "
          + anchor.uuid() + " to " + anchorOrigin + ".");
    }
  }

  private CompletableFuture<Boolean> beginTeleport(EntityRef entity, Loc destination, String leg) {
    try {
      return entity.teleport(destination, TeleportCause.PLUGIN);
    } catch (RuntimeException error) {
      reportTeleportFailure(entity, leg, error);
      return null;
    }
  }

  private void reportTeleportFailure(EntityRef entity, String leg, Throwable failure) {
    if (failure == null) {
      return;
    }
    AdaptLog.error(name + " " + leg + " teleport failed for " + entity.uuid() + ".");
    AdaptLog.error(failure);
  }

  static boolean teleportCompleted(Boolean success, Throwable failure) {
    return failure == null && Boolean.TRUE.equals(success);
  }

  static boolean canStartPlayerLeg(boolean currentInFlight, boolean runtimeRegistered) {
    return currentInFlight && runtimeRegistered;
  }
}
