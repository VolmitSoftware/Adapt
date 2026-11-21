package art.arcane.adapt.client.protocol;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Negotiated capability per player. Safe to read from any thread. */
public final class ClientCapabilities {
  private final Map<UUID, ClientCapability> capabilities = new ConcurrentHashMap<>();

  /** Stores the negotiated capability, or forgets the player when the hello withdraws. */
  public Optional<ClientCapability> accept(UUID playerId, int clientVersion, int clientMask, int serverMask) {
    Objects.requireNonNull(playerId, "playerId");
    Optional<ClientCapability> accepted = ClientNegotiation.accept(clientVersion, clientMask, serverMask);
    if (accepted.isPresent()) {
      capabilities.put(playerId, accepted.get());
    } else {
      capabilities.remove(playerId);
    }
    return accepted;
  }

  public Optional<ClientCapability> capability(UUID playerId) {
    return playerId == null ? Optional.empty() : Optional.ofNullable(capabilities.get(playerId));
  }

  public boolean has(UUID playerId, int capabilityBit) {
    ClientCapability capability = playerId == null ? null : capabilities.get(playerId);
    return capability != null && capability.has(capabilityBit);
  }

  public boolean clear(UUID playerId) {
    return playerId != null && capabilities.remove(playerId) != null;
  }

  public void clearAll() {
    capabilities.clear();
  }
}
