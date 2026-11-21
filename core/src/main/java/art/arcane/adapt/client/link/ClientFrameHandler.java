package art.arcane.adapt.client.link;

import art.arcane.adapt.client.protocol.ClientCapability;
import art.arcane.adapt.client.protocol.ClientFrame;

import java.util.UUID;

/**
 * Receives what {@link ClientSessions} accepted. Calls arrive on the transport's thread; the implementation moves work
 * to the player's owning thread.
 */
public interface ClientFrameHandler {
  /** The player's client negotiated (or renegotiated) and the {@code Accept} frame was sent. */
  void negotiated(UUID playerId, ClientCapability capability);

  /** The player is back on the fallback UI: they withdrew, quit, or were reset for protocol violations. */
  void withdrawn(UUID playerId);

  /** Any frame other than {@code Hello}, from a negotiated player. */
  void handle(UUID playerId, ClientFrame frame);
}
