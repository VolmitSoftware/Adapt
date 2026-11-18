package art.arcane.adapt.bukkit.platform.entity;

import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.value.Loc;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Folia never fires the player teleport event for asynchronous teleports, so the bus would miss every
 * player teleport there. On a regionized server the core event is posted on the player's own region
 * before moving, with the cancel and destination semantics the Bukkit bridge gives on Paper.
 */
final class RegionTeleports {
  private RegionTeleports() {
  }

  static CompletableFuture<Boolean> announce(AdaptScheduler scheduler, AdaptEventBus bus, PlayerRef player, Supplier<Loc> origin,
                                             Loc destination, TeleportCause cause, Function<Loc, CompletableFuture<Boolean>> move) {
    CompletableFuture<Boolean> result = new CompletableFuture<>();
    Runnable start = () -> {
      PlayerTeleportEvent event = new PlayerTeleportEvent(player, origin.get(), destination, cause);
      bus.post(event);
      if (event.isCancelled()) {
        result.complete(false);
        return;
      }
      move.apply(event.to()).whenComplete((Boolean moved, Throwable failure) -> {
        if (failure != null) {
          result.completeExceptionally(failure);
        } else {
          result.complete(Boolean.TRUE.equals(moved));
        }
      });
    };
    if (scheduler.owns(player)) {
      start.run();
    } else if (!scheduler.runEntity(player, start, 0L, () -> result.complete(false))) {
      result.complete(false);
    }
    return result;
  }
}
