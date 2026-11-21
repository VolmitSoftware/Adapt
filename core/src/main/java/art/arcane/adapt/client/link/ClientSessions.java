package art.arcane.adapt.client.link;

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.client.protocol.ClientCapabilities;
import art.arcane.adapt.client.protocol.ClientCapability;
import art.arcane.adapt.client.protocol.ClientFrame;
import art.arcane.adapt.client.protocol.ClientProtocolException;
import art.arcane.adapt.client.protocol.FrameCodec;
import art.arcane.adapt.client.protocol.FrameFragments;
import art.arcane.adapt.client.protocol.FrameLimits;
import art.arcane.adapt.client.protocol.FrameRateLimiter;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.protocol.ServerInfo;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Server side of the client protocol: negotiates on {@code Hello}, keeps each player's capability, rate-limits and
 * decodes inbound frames and passes the rest to the {@link ClientFrameHandler}. Thread-safe.
 */
public final class ClientSessions {
  private final ServerInfo server;
  private final ClientFrameHandler handler;
  private final Supplier<ClientLink> link;
  private final LongSupplier clock;
  private final ClientCapabilities capabilities = new ClientCapabilities();
  private final Map<UUID, FrameRateLimiter> limiters = new ConcurrentHashMap<>();
  private final Map<UUID, FrameFragments.Assembler> assemblers = new ConcurrentHashMap<>();
  private final Map<UUID, Object> outbound = new ConcurrentHashMap<>();

  public ClientSessions(ServerInfo server, ClientFrameHandler handler) {
    this(server, handler, ClientLink::current, System::currentTimeMillis);
  }

  public ClientSessions(ServerInfo server, ClientFrameHandler handler, Supplier<ClientLink> link, LongSupplier clock) {
    this.server = Objects.requireNonNull(server, "server");
    this.handler = Objects.requireNonNull(handler, "handler");
    this.link = Objects.requireNonNull(link, "link");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Handles one inbound payload from the player's connection. */
  public void receive(UUID playerId, byte[] payload) {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(payload, "payload");
    FrameRateLimiter limiter = limiters.computeIfAbsent(playerId, (UUID ignored) -> new FrameRateLimiter());
    long now = clock.getAsLong();
    FrameRateLimiter.Verdict verdict = limiter.admit(now, payload.length);
    if (verdict != FrameRateLimiter.Verdict.ACCEPT) {
      refuse(playerId, limiter, verdict);
      return;
    }
    FrameFragments.Assembler assembler = assemblers.computeIfAbsent(playerId,
        (UUID ignored) -> new FrameFragments.Assembler(FrameLimits.MAX_C2S_BYTES));
    ClientFrame frame;
    try {
      byte[] whole;
      synchronized (assembler) {
        whole = assembler.accept(payload);
      }
      if (whole == null) {
        return;
      }
      frame = FrameCodec.decodeClient(whole);
    } catch (ClientProtocolException exception) {
      refuse(playerId, limiter, limiter.violation(now, exception.getMessage()));
      return;
    }
    if (frame instanceof ClientFrame.Hello hello) {
      hello(playerId, hello);
      return;
    }
    if (capabilities.capability(playerId).isPresent()) {
      handler.handle(playerId, frame);
    }
  }

  /** Forgets the player; call when they leave the server. */
  public void quit(UUID playerId) {
    limiters.remove(playerId);
    assemblers.remove(playerId);
    outbound.remove(playerId);
    if (capabilities.clear(playerId)) {
      handler.withdrawn(playerId);
    }
  }

  public boolean has(UUID playerId, int capabilityBit) {
    return capabilities.has(playerId, capabilityBit);
  }

  public Optional<ClientCapability> capability(UUID playerId) {
    return capabilities.capability(playerId);
  }

  /** True when the player negotiated a client and its channel takes frames now. */
  public boolean ready(UUID playerId) {
    return capabilities.capability(playerId).isPresent() && link.get().open(playerId);
  }

  /**
   * Encodes and sends a frame to a negotiated player whose channel is open. Returns false when the player has no
   * client or the send failed; a failed send warns once and puts the player back on the fallback UI.
   */
  public boolean send(UUID playerId, ServerFrame frame) {
    ClientLink active = link.get();
    if (capabilities.capability(playerId).isEmpty() || !active.open(playerId)) {
      return false;
    }
    try {
      deliver(active, playerId, FrameCodec.encodeServer(frame));
      return true;
    } catch (ClientProtocolException | RuntimeException exception) {
      if (capabilities.clear(playerId)) {
        AdaptLog.warn("Could not send " + frame.getClass().getSimpleName() + " to " + playerId + ": "
            + reason(exception) + "; using the fallback UI for this player until they reconnect");
        handler.withdrawn(playerId);
      }
      return false;
    }
  }

  private void hello(UUID playerId, ClientFrame.Hello hello) {
    boolean wasNegotiated = capabilities.capability(playerId).isPresent();
    Optional<ClientCapability> accepted = capabilities.accept(playerId, hello.version(), hello.capabilityMask(),
        server.capabilityMask());
    if (accepted.isEmpty()) {
      if (wasNegotiated) {
        handler.withdrawn(playerId);
      }
      return;
    }
    ClientCapability capability = accepted.get();
    ServerInfo negotiated = new ServerInfo(server.platform(), server.minecraftVersion(), capability.mask());
    try {
      deliver(link.get(), playerId, FrameCodec.encodeServer(new ServerFrame.Accept(capability.version(), negotiated)));
    } catch (ClientProtocolException | RuntimeException exception) {
      capabilities.clear(playerId);
      AdaptLog.warn("Could not accept the Adapt client of " + playerId + ": " + reason(exception));
      if (wasNegotiated) {
        handler.withdrawn(playerId);
      }
      return;
    }
    handler.negotiated(playerId, capability);
  }

  /** Sends a frame whole or as back-to-back fragments; one player's frames never interleave across threads. */
  private void deliver(ClientLink active, UUID playerId, byte[] frame) throws ClientProtocolException {
    List<byte[]> payloads = FrameFragments.split(frame, active.maxPayloadBytes());
    synchronized (outbound.computeIfAbsent(playerId, (UUID ignored) -> new Object())) {
      for (byte[] payload : payloads) {
        active.send(playerId, payload);
      }
    }
  }

  private static String reason(Exception exception) {
    return exception instanceof ClientProtocolException ? exception.getMessage() : exception.toString();
  }

  private void refuse(UUID playerId, FrameRateLimiter limiter, FrameRateLimiter.Verdict verdict) {
    if (verdict != FrameRateLimiter.Verdict.RESET) {
      return;
    }
    AdaptLog.warn("Dropped the Adapt client UI of " + playerId + " after " + limiter.summary());
    if (capabilities.clear(playerId)) {
      handler.withdrawn(playerId);
    }
  }
}
