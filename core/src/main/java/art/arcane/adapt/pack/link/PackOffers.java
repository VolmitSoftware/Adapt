package art.arcane.adapt.pack.link;

import art.arcane.adapt.AdaptConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * When a player is offered the pack, and how they are pushed towards it. A player is considered once the client mod
 * handshake window after joining has passed and their data is loaded: players whose Adapt client negotiated and Bedrock
 * players are never offered nor nudged. {@link Policy#JOIN} offers then, {@link Policy#FIRST_OPEN} on the first Adapt
 * menu, {@link Policy#OFF} only when the player asks. While {@link Switches#required} is on, a player without
 * {@link Players#optional the bypass} is offered at join under every policy, with the pack marked required.
 * <p>
 * A vanilla client that declined turns later offers down without asking for the rest of the connection, so nothing is
 * offered again unasked and an asked offer is answered {@link Result#CLIENT_DECLINED}. Later joins offer again unless
 * {@link Switches#reoffer} is off: then a declined pack waits until its hash changes or the player asks. A declined
 * required pack disconnects the player. Otherwise the first decline of a connection is answered with the steps that let
 * the client load server packs again, a player not offered at join is invited once, and opening an Adapt menu without
 * the pack nudges again at most every {@link #NUDGE_MILLIS}: the steps after a decline, else an invitation that offers
 * the pack again. {@link Switches#joinNudge} and {@link Switches#menuNudge} turn those off.
 * <p>
 * A new artifact, or the same one at a new address, replaces the old offer on every client that had one. With
 * {@link PackViewers#merged merged} viewers nothing is offered. Thread-safe.
 */
public final class PackOffers {
  /** How often a passed window looks again for player data that is not loaded yet. */
  static final long READ_RETRY_MILLIS = 500L;
  /** The shortest time between two nudges to one player. */
  static final long NUDGE_MILLIS = 120_000L;
  private final PackLink link;
  private final PackViewers viewers;
  private final Players players;
  private final Switches switches;
  private final Delay delay;
  private final LongSupplier clock;
  private final Policy policy;
  private final long windowMillis;
  private final Map<UUID, Session> sessions = new HashMap<>();
  private Published current;

  public PackOffers(PackLink link, PackViewers viewers, Players players, Switches switches, Delay delay,
                    LongSupplier clock, Policy policy, long windowMillis) {
    this.link = Objects.requireNonNull(link, "link");
    this.viewers = Objects.requireNonNull(viewers, "viewers");
    this.players = Objects.requireNonNull(players, "players");
    this.switches = Objects.requireNonNull(switches, "switches");
    this.delay = Objects.requireNonNull(delay, "delay");
    this.clock = Objects.requireNonNull(clock, "clock");
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
    if (session == null || !session.ready) {
      if (session != null && policy == Policy.FIRST_OPEN) {
        session.wanted = true;
      }
      return;
    }
    if (policy == Policy.FIRST_OPEN && !session.wanted) {
      session.wanted = true;
      if (due(playerId, session)) {
        offer(playerId, session, false);
        return;
      }
    }
    if (switches.menuNudge()) {
      nudge(playerId, session);
    }
  }

  /** The player asked for the pack: sends it again even when they declined it on this connection or already hold it. */
  public synchronized Result requested(UUID playerId) {
    Session session = sessions.computeIfAbsent(playerId, (UUID ignored) -> new Session());
    session.ready = true;
    session.wanted = true;
    players.declined(playerId, null);
    return offer(playerId, session, true);
  }

  /** What the player sees about the pack now, for an invitation in a chest menu. */
  public synchronized Standing standing(UUID playerId) {
    Session session = sessions.get(playerId);
    if (session == null || !eligible(playerId) || viewers.loaded(playerId)) {
      return Standing.NONE;
    }
    return session.declined ? Standing.REFUSING : Standing.INVITED;
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
      } else if (session.offered != null || due(playerId, session)) {
        offer(playerId, session, false);
      }
    }
  }

  /** One client report; the platform link delivers these. */
  public synchronized void status(UUID playerId, UUID packId, PackState state) {
    if (!viewers.status(playerId, packId, state)) {
      return;
    }
    Session session = sessions.get(playerId);
    if (session == null) {
      return;
    }
    if (players.clientMod(playerId)) {
      withdraw(playerId, session);
      return;
    }
    if (state != PackState.DECLINED || session.declined) {
      return;
    }
    session.declined = true;
    Published published = current;
    if (published != null && published.artifact.id().equals(packId)) {
      players.declined(playerId, published.artifact.sha1Hex());
    }
    if (session.required) {
      players.kick(playerId);
    } else if (switches.joinNudge()) {
      session.nudged = clock.getAsLong();
      players.steps(playerId);
    }
  }

  private void windowPassed(UUID playerId, Session session) {
    synchronized (this) {
      if (sessions.get(playerId) != session || session.ready) {
        return;
      }
      if (!players.ready(playerId)) {
        delay.after(playerId, READ_RETRY_MILLIS, () -> windowPassed(playerId, session));
        return;
      }
      session.ready = true;
      if (due(playerId, session)) {
        offer(playerId, session, false);
      } else if (switches.joinNudge()) {
        nudge(playerId, session);
      }
    }
  }

  /** An invitation, or the steps after a decline, when the player lacks the pack and was not nudged lately. */
  private void nudge(UUID playerId, Session session) {
    long now = clock.getAsLong();
    if (!eligible(playerId) || viewers.loaded(playerId) || loading(playerId)
        || session.nudged != Long.MIN_VALUE && now - session.nudged < NUDGE_MILLIS) {
      return;
    }
    session.nudged = now;
    if (session.declined) {
      players.steps(playerId);
    } else {
      players.invite(playerId);
    }
  }

  /** True while the client said yes to the current offer and has not finished loading it. */
  private boolean loading(UUID playerId) {
    PackState state = viewers.state(playerId).orElse(null);
    return state == PackState.ACCEPTED || state == PackState.DOWNLOADED;
  }

  /** True when an offer to this player could be sent now. */
  private boolean eligible(UUID playerId) {
    Published published = current;
    return !viewers.merged() && published != null && published.url != null && link.available()
        && !players.clientMod(playerId) && !players.bedrock(playerId);
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
    if (players.clientMod(playerId)) {
      return Result.CLIENT_MOD;
    }
    if (players.bedrock(playerId)) {
      return Result.BEDROCK;
    }
    if (!asked && session.declined) {
      return Result.CLIENT_DECLINED;
    }
    UUID packId = published.artifact.id();
    if (!asked && packId.equals(session.offered) && published.url.equals(session.url)) {
      return Result.OFFERED;
    }
    if (session.offered != null) {
      link.remove(playerId, session.offered);
    }
    session.offered = packId;
    session.url = published.url;
    session.required = required(playerId);
    viewers.offered(playerId, packId);
    link.offer(playerId, new PackOffer(packId, published.url, published.artifact.sha1Hex(), players.prompt(playerId),
        session.required));
    return session.declined ? Result.CLIENT_DECLINED : Result.OFFERED;
  }

  /** Whether an offer goes to this player unasked now. */
  private boolean due(UUID playerId, Session session) {
    if (!session.ready) {
      return false;
    }
    if (required(playerId)) {
      return true;
    }
    Published published = current;
    if (!switches.reoffer() && published != null && published.artifact.sha1Hex().equals(players.declined(playerId))) {
      return false;
    }
    return policy == Policy.JOIN || policy == Policy.FIRST_OPEN && session.wanted;
  }

  private boolean required(UUID playerId) {
    return switches.required() && !players.optional(playerId);
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
    /** The client turned the pack down on this connection, and a vanilla client turns it down again unasked. */
    CLIENT_DECLINED
  }

  /** Where a player stands with the pack, as far as an invitation cares. */
  public enum Standing {
    /** No invitation: they have it, cannot get it, or nothing is offered here. */
    NONE,
    /** An offer can go out. */
    INVITED,
    /** Their client turns server packs down on this connection; only changing the server entry helps. */
    REFUSING
  }

  /** What offers need to know about a player, and what they tell them. Called under the offers' lock. */
  public interface Players {
    /** Whether the player's data is loaded, so the prompt and messages can be put in their language. */
    boolean ready(UUID playerId);

    /** The player's Adapt client mod negotiated; they never get the pack. */
    boolean clientMod(UUID playerId);

    boolean bedrock(UUID playerId);

    /** The player holds the bypass that keeps the pack optional while it is required. */
    boolean optional(UUID playerId);

    /** The text the client shows in its pack prompt. */
    String prompt(UUID playerId);

    /** The SHA-1 hex of the pack the player last declined, kept across joins; null for none. */
    String declined(UUID playerId);

    /** Records the pack the player declined; null forgets it. */
    void declined(UUID playerId, String sha1Hex);

    /** Tells the player the pack is there, with a way to get it offered again. */
    void invite(UUID playerId);

    /** Tells the player how to let their client load server packs for this server. */
    void steps(UUID playerId);

    /** Disconnects a player who declined a required pack. */
    void kick(UUID playerId);
  }

  /** The {@code pack} config switches offers follow. */
  public interface Switches {
    /** The live config, read on every use so a reload applies to later offers. */
    Switches CONFIG = new Switches() {
      @Override
      public boolean required() {
        return AdaptConfig.get().getPack().isRequired();
      }

      @Override
      public boolean reoffer() {
        return AdaptConfig.get().getPack().isReofferAfterDecline();
      }

      @Override
      public boolean joinNudge() {
        return AdaptConfig.get().getPack().isJoinNudge();
      }

      @Override
      public boolean menuNudge() {
        return AdaptConfig.get().getPack().isMenuNudge();
      }
    };

    /** Whether the pack goes out as required: the client prompts even with server packs disabled, a decline disconnects. */
    boolean required();

    /** Whether a player who declined is offered the pack unasked again on later joins. */
    boolean reoffer();

    /** Whether a player is told about the pack once after joining and after declining it. */
    boolean joinNudge();

    /** Whether opening an Adapt menu without the pack tells the player about it again. */
    boolean menuNudge();
  }

  /** Runs a task for a player after a delay, on a thread that may talk to that player; drops it if they left. */
  @FunctionalInterface
  public interface Delay {
    void after(UUID playerId, long millis, Runnable task);
  }

  private static final class Session {
    private boolean ready;
    private boolean wanted;
    private boolean declined;
    private boolean required;
    private long nudged = Long.MIN_VALUE;
    private UUID offered;
    private String url;
  }

  private record Published(PackArtifact artifact, String url) {
  }
}
