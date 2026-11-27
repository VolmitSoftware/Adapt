package art.arcane.adapt.pack.link;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * When a player is offered the pack. A player is considered once the client mod handshake window after joining has
 * passed: players whose Adapt client negotiated and Bedrock players are never offered. {@link Policy#JOIN} offers
 * then, {@link Policy#FIRST_OPEN} on the first Adapt menu, {@link Policy#OFF} only when the player asks or picks book
 * menus. A declined pack is not offered again until its hash changes or the player asks; {@link Declines} keeps that
 * across restarts, and the window waits until it can read the player's. A vanilla client that declined on this
 * connection turns later offers down without asking, so an offer to it is answered {@link Result#CLIENT_DECLINED}. A
 * new artifact, or the same one at a new address, replaces the old offer on every client that had one. Offers are
 * never forced. With {@link PackViewers#merged merged} viewers nothing is offered. Thread-safe.
 */
public final class PackOffers {
  /** How often a passed window looks again for player data that is not loaded yet. */
  static final long READ_RETRY_MILLIS = 500L;
  private final PackLink link;
  private final PackViewers viewers;
  private final Declines declines;
  private final Predicate<UUID> clientMod;
  private final Predicate<UUID> bedrock;
  private final Function<UUID, String> prompt;
  private final Delay delay;
  private final Policy policy;
  private final long windowMillis;
  private final Map<UUID, Session> sessions = new HashMap<>();
  private Published current;

  public PackOffers(PackLink link, PackViewers viewers, Declines declines, Predicate<UUID> clientMod,
                    Predicate<UUID> bedrock, Function<UUID, String> prompt, Delay delay, Policy policy, long windowMillis) {
    this.link = Objects.requireNonNull(link, "link");
    this.viewers = Objects.requireNonNull(viewers, "viewers");
    this.declines = Objects.requireNonNull(declines, "declines");
    this.clientMod = Objects.requireNonNull(clientMod, "clientMod");
    this.bedrock = Objects.requireNonNull(bedrock, "bedrock");
    this.prompt = Objects.requireNonNull(prompt, "prompt");
    this.delay = Objects.requireNonNull(delay, "delay");
    this.policy = Objects.requireNonNull(policy, "policy");
    this.windowMillis = Math.max(0L, windowMillis);
  }

  public synchronized void joined(UUID playerId) {
    Session session = new Session();
    sessions.put(playerId, session);
    delay.after(playerId, windowMillis, () -> windowPassed(playerId, session));
  }

  /** A player already online when offering started, such as after a reload; the window is long over. */
  public synchronized void present(UUID playerId) {
    Session session = sessions.computeIfAbsent(playerId, (UUID ignored) -> new Session());
    windowPassed(playerId, session);
  }

  /** The player opened an Adapt menu. */
  public synchronized void opened(UUID playerId) {
    Session session = sessions.get(playerId);
    if (session == null || policy != Policy.FIRST_OPEN || session.wanted) {
      return;
    }
    session.wanted = true;
    if (session.ready) {
      offer(playerId, session, false);
    }
  }

  /** The player asked for the pack: sends it again even when they declined or already hold it. */
  public synchronized Result requested(UUID playerId) {
    Session session = sessions.computeIfAbsent(playerId, (UUID ignored) -> new Session());
    session.ready = true;
    session.wanted = true;
    declines.set(playerId, null);
    return offer(playerId, session, true);
  }

  /** The player picked book menus: sends the pack under any policy unless they declined this one. */
  public synchronized Result bookChosen(UUID playerId) {
    Session session = sessions.computeIfAbsent(playerId, (UUID ignored) -> new Session());
    session.ready = true;
    session.wanted = true;
    return offer(playerId, session, false);
  }

  public synchronized void quit(UUID playerId) {
    sessions.remove(playerId);
    viewers.quit(playerId);
  }

  /**
   * Makes {@code artifact}, downloadable at {@code url}, the pack to offer; a null url means nothing hosts it, a null
   * artifact means none was built. Either withdraws the pack from every client.
   */
  public synchronized void published(PackArtifact artifact, String url) {
    Published next = artifact == null ? null : new Published(artifact, url);
    Published previous = current;
    if (Objects.equals(previous, next)) {
      return;
    }
    current = next;
    for (Map.Entry<UUID, Session> entry : sessions.entrySet()) {
      UUID playerId = entry.getKey();
      Session session = entry.getValue();
      if (next == null || next.url == null) {
        withdraw(playerId, session);
      } else if (session.offered != null || session.due(policy)) {
        offer(playerId, session, false);
      }
    }
  }

  /** One client report; the platform link delivers these. */
  public synchronized void status(UUID playerId, UUID packId, PackState state) {
    if (!viewers.status(playerId, packId, state)) {
      return;
    }
    Published published = current;
    if (state == PackState.DECLINED && published != null && published.artifact.id().equals(packId)) {
      declines.set(playerId, published.artifact.sha1Hex());
    }
    Session session = sessions.get(playerId);
    if (session != null && clientMod.test(playerId)) {
      withdraw(playerId, session);
    }
  }

  private void windowPassed(UUID playerId, Session session) {
    synchronized (this) {
      if (sessions.get(playerId) != session || session.ready) {
        return;
      }
      if (!declines.ready(playerId)) {
        delay.after(playerId, READ_RETRY_MILLIS, () -> windowPassed(playerId, session));
        return;
      }
      session.ready = true;
      if (session.due(policy)) {
        offer(playerId, session, false);
      }
    }
  }

  private Result offer(UUID playerId, Session session, boolean asked) {
    if (viewers.merged()) {
      return Result.UNAVAILABLE;
    }
    Published published = current;
    if (published == null) {
      return Result.NO_PACK;
    }
    if (published.url == null) {
      return Result.UNHOSTED;
    }
    if (!link.available()) {
      return Result.UNAVAILABLE;
    }
    if (clientMod.test(playerId)) {
      return Result.CLIENT_MOD;
    }
    if (bedrock.test(playerId)) {
      return Result.BEDROCK;
    }
    boolean turnedDown = session.offered != null && viewers.state(playerId).orElse(null) == PackState.DECLINED;
    UUID packId = published.artifact.id();
    if (!asked && published.artifact.sha1Hex().equals(declines.get(playerId))) {
      return turnedDown ? Result.CLIENT_DECLINED : Result.DECLINED;
    }
    if (!asked && packId.equals(session.offered) && published.url.equals(session.url)) {
      return Result.OFFERED;
    }
    if (session.offered != null) {
      link.remove(playerId, session.offered);
    }
    session.offered = packId;
    session.url = published.url;
    viewers.offered(playerId, packId);
    link.offer(playerId, new PackOffer(packId, published.url, published.artifact.sha1Hex(), prompt.apply(playerId)));
    return turnedDown ? Result.CLIENT_DECLINED : Result.OFFERED;
  }

  private void withdraw(UUID playerId, Session session) {
    if (session.offered == null) {
      return;
    }
    link.remove(playerId, session.offered);
    session.offered = null;
    session.url = null;
    viewers.removed(playerId);
  }

  public enum Policy {
    JOIN,
    FIRST_OPEN,
    OFF
  }

  /** The outcome of a requested offer. */
  public enum Result {
    OFFERED,
    NO_PACK,
    UNHOSTED,
    UNAVAILABLE,
    CLIENT_MOD,
    BEDROCK,
    DECLINED,
    /** Sent, but the client turned the pack down on this connection and a vanilla client turns it down again unasked. */
    CLIENT_DECLINED
  }

  /** Where the hash of the pack each player last declined lives. */
  public interface Declines {
    /** Whether the player's record can be read yet; {@link #get} answers null until it can. */
    boolean ready(UUID playerId);

    /** The declined pack's SHA-1 hex, or null. */
    String get(UUID playerId);

    /** Records a decline; null forgets it. */
    void set(UUID playerId, String sha1Hex);

    /** Declines kept in memory only. */
    static Declines memory() {
      Map<UUID, String> declined = new ConcurrentHashMap<>();
      return new Declines() {
        @Override
        public boolean ready(UUID playerId) {
          return true;
        }

        @Override
        public String get(UUID playerId) {
          return declined.get(playerId);
        }

        @Override
        public void set(UUID playerId, String sha1Hex) {
          if (sha1Hex == null) {
            declined.remove(playerId);
          } else {
            declined.put(playerId, sha1Hex);
          }
        }
      };
    }
  }

  /** Runs a task for a player after a delay, on a thread that may talk to that player; drops it if they left. */
  @FunctionalInterface
  public interface Delay {
    void after(UUID playerId, long millis, Runnable task);
  }

  private static final class Session {
    private boolean ready;
    private boolean wanted;
    private UUID offered;
    private String url;

    private boolean due(Policy policy) {
      return ready && (policy == Policy.JOIN || policy == Policy.FIRST_OPEN && wanted);
    }
  }

  private record Published(PackArtifact artifact, String url) {
  }
}
