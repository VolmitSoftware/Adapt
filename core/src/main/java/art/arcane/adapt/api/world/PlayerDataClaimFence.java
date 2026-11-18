package art.arcane.adapt.api.world;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orders login claims against player retirement. A claim opens a ticket before it reads storage; retiring a
 * player (which queues its final save) revokes every open ticket for that player, so a claim that read storage
 * before the final save was queued can never be cached or activated afterwards.
 */
final class PlayerDataClaimFence {
  private final ConcurrentMap<UUID, Set<Ticket>> openTickets = new ConcurrentHashMap<>();

  Ticket open(UUID playerId) {
    Ticket ticket = new Ticket();
    openTickets.compute(playerId, (UUID ignored, Set<Ticket> tickets) -> {
      Set<Ticket> registered = tickets == null ? ConcurrentHashMap.newKeySet() : tickets;
      registered.add(ticket);
      return registered;
    });
    return ticket;
  }

  /** Closes {@code ticket}; true only when no retirement revoked it since it was opened. */
  boolean close(UUID playerId, Ticket ticket) {
    Objects.requireNonNull(ticket);
    AtomicBoolean current = new AtomicBoolean();
    openTickets.computeIfPresent(playerId, (UUID ignored, Set<Ticket> tickets) -> {
      current.set(tickets.remove(ticket));
      return tickets.isEmpty() ? null : tickets;
    });
    return current.get();
  }

  void revoke(UUID playerId) {
    openTickets.remove(playerId);
  }

  void clear() {
    openTickets.clear();
  }

  static final class Ticket {
    private Ticket() {
    }
  }

  record Claim(LoadedPlayerData data, Ticket ticket) {
    Claim {
      Objects.requireNonNull(data);
      Objects.requireNonNull(ticket);
    }
  }
}
