package art.arcane.adapt.util.common.input;

import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerChangedWorldEvent;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerGameModeChangeEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerRespawnEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleFlightEvent;
import art.arcane.volmlib.util.math.M;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Mid-air jump presses on servers without the player input packet (before 1.21.2), where the server never sees the
 * jump key. While a {@link DoubleJumpGesture} listens to an airborne survival or adventure player who may not fly, the
 * player is granted flight requests ({@link PlayerRef#grantFlightRequests()}): a double tap of jump then makes the
 * client ask to start flying, which is cancelled and counted as one press. The grant never lets the player fly and
 * never changes fall damage. It ends on landing, when no gesture has listened for {@link #LISTEN_TIMEOUT_MILLIS}
 * (adaptation unlearned or disabled), and on teleport, world change, game mode change, death, respawn, quit and
 * shutdown. A marker in the player's data removes a grant that a crash saved with the player on the next join.
 */
public final class MidairJumps {
  static final long LISTEN_TIMEOUT_MILLIS = 500L;
  static final DataKey<Byte> GRANT_MARKER = DataKey.adapt("midair_jump_grant", DataCodecs.BYTE);
  private static volatile MidairJumps active;

  private final LongSupplier clock;
  private final Map<UUID, Listened> players = new ConcurrentHashMap<>();

  MidairJumps(LongSupplier clock) {
    this.clock = clock;
  }

  /** The running instance, or null when the server reports jump input itself. */
  public static MidairJumps active() {
    return active;
  }

  /** Starts listening for flight requests; the caller registers the returned listener. */
  public static MidairJumps start() {
    MidairJumps jumps = new MidairJumps(M::ms);
    active = jumps;
    return jumps;
  }

  /** Ends every grant and stops counting presses. */
  public static void stop() {
    MidairJumps jumps = active;
    active = null;
    if (jumps != null) {
      jumps.revokeAll();
    }
  }

  /**
   * Called by {@code owner} on each move of a player it listens to: grants flight requests while the player is
   * airborne and ends the grant on landing. Returns the player's press count; a change means a mid-air press.
   */
  public long listen(PlayerRef player, Object owner) {
    Listened state = players.computeIfAbsent(player.uuid(), unused -> new Listened(player));
    state.player = player;
    state.owners.put(owner, clock.getAsLong());
    if (player.isOnGround()) {
      revoke(state);
    } else if (!state.granted && canGrant(player) && player.grantFlightRequests()) {
      state.granted = true;
      player.data().set(GRANT_MARKER, (byte) 1);
    }
    return state.presses;
  }

  /** {@code owner} no longer listens to the player; the grant ends when nobody else does. */
  public void stopListening(UUID playerId, Object owner) {
    Listened state = players.get(playerId);
    if (state == null) {
      return;
    }
    state.owners.remove(owner);
    if (state.owners.isEmpty()) {
      players.remove(playerId, state);
      revoke(state);
    }
  }

  /** True while the player holds a grant from this class. */
  public boolean granted(UUID playerId) {
    Listened state = players.get(playerId);
    return state != null && state.granted;
  }

  @Handle(priority = EventPriority.LOWEST)
  public void on(PlayerToggleFlightEvent e) {
    Listened state = players.get(e.player().uuid());
    if (state == null || !state.granted || !e.isFlying() || !e.player().flightRequestsGranted()) {
      return;
    }
    e.setCancelled(true);
    state.presses++;
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    Listened state = players.get(e.player().uuid());
    if (state == null) {
      return;
    }
    long now = clock.getAsLong();
    state.owners.values().removeIf(last -> now - last > LISTEN_TIMEOUT_MILLIS);
    if (state.owners.isEmpty()) {
      players.remove(e.player().uuid(), state);
      revoke(state);
    } else if (e.player().isOnGround()) {
      revoke(state);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerTeleportEvent e) {
    revoke(e.player());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerChangedWorldEvent e) {
    revoke(e.player());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerGameModeChangeEvent e) {
    revoke(e.player());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerDeathEvent e) {
    revoke(e.player());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerRespawnEvent e) {
    revoke(e.player());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerQuitEvent e) {
    Listened state = players.remove(e.player().uuid());
    if (state != null) {
      revoke(state);
    }
  }

  @Handle(priority = EventPriority.LOWEST, ignoreCancelled = false)
  public void on(PlayerJoinEvent e) {
    dropLeftover(e.player());
  }

  /**
   * Removes a grant an earlier run saved with the player and never ended (a crash): the marker is set, the player
   * may fly in survival or adventure mode and holds no grant of this run.
   */
  void dropLeftover(PlayerRef player) {
    if (!player.data().has(GRANT_MARKER) || player.flightRequestsGranted()) {
      return;
    }
    if (survivalLike(player) && player.allowFlight()) {
      player.setFlying(false);
      player.setAllowFlight(false);
    }
    player.data().remove(GRANT_MARKER);
  }

  private void revoke(PlayerRef player) {
    Listened state = players.get(player.uuid());
    if (state != null) {
      revoke(state);
    }
  }

  private void revokeAll() {
    for (Listened state : List.copyOf(players.values())) {
      if (state.player.isOnline()) {
        revoke(state);
      }
    }
    players.clear();
  }

  private static void revoke(Listened state) {
    if (!state.granted) {
      return;
    }
    state.granted = false;
    state.player.revokeFlightRequests();
    state.player.data().remove(GRANT_MARKER);
  }

  private static boolean canGrant(PlayerRef player) {
    return survivalLike(player) && !player.isDead() && !player.isFlying() && !player.isGliding()
        && player.vehicle() == null && !player.allowFlight();
  }

  private static boolean survivalLike(PlayerRef player) {
    GameMode mode = player.gameMode();
    return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
  }

  private static final class Listened {
    private final Map<Object, Long> owners = new ConcurrentHashMap<>();
    private volatile PlayerRef player;
    private volatile boolean granted;
    private volatile long presses;

    private Listened(PlayerRef player) {
      this.player = player;
    }
  }
}
