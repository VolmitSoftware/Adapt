package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.value.Loc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tells a rift teleport (an ender pearl, chorus fruit or a blink) from any other. Blinks and anchor trips teleport as
 * a plugin, so each names its destination here just before it teleports. Phase Step blinks and anchor trips pay no
 * skill XP, and a Phase Step blink sets off the Rift Trigger at most once per {@link #PHASE_TRIGGER_MILLIS}.
 */
public final class RiftTeleports {
  public static final long PHASE_TRIGGER_MILLIS = 4_000L;
  private static final long BLINK_MEMORY_MILLIS = 5_000L;
  private static final double SAME_SPOT_SQUARED = 1D;
  private static final Map<UUID, Blink> BLINKS = new ConcurrentHashMap<>();
  private static final Map<UUID, Long> PHASE_TRIGGERS = new ConcurrentHashMap<>();

  private RiftTeleports() {
  }

  private enum Kind { BLINK, PHASE, TRAVEL }

  /** {@code player} is about to blink to {@code destination}. */
  public static void blinking(PlayerRef player, Loc destination) {
    remember(player, destination, Kind.BLINK);
  }

  /** {@code player} is about to Phase Step to {@code destination}. */
  public static void phasing(PlayerRef player, Loc destination) {
    remember(player, destination, Kind.PHASE);
  }

  /** {@code player} is about to travel to an anchor at {@code destination}; not a rift teleport for the blocks. */
  public static void travelling(PlayerRef player, Loc destination) {
    remember(player, destination, Kind.TRAVEL);
  }

  public static boolean byRift(PlayerTeleportEvent e) {
    TeleportCause cause = e.cause();
    if (cause == TeleportCause.ENDER_PEARL || cause == TeleportCause.CHORUS_FRUIT) {
      return true;
    }
    Kind kind = kind(e);
    return kind == Kind.BLINK || kind == Kind.PHASE;
  }

  /** A Phase Step blink or an anchor trip, which pay no skill XP. */
  public static boolean paysNoXp(PlayerTeleportEvent e) {
    Kind kind = kind(e);
    return kind == Kind.PHASE || kind == Kind.TRAVEL;
  }

  /**
   * Whether this rift teleport may set off the Rift Trigger: always, except a Phase Step blink within
   * {@link #PHASE_TRIGGER_MILLIS} of the last one that did.
   */
  public static boolean mayTrigger(PlayerTeleportEvent e, long now) {
    if (kind(e) != Kind.PHASE) {
      return true;
    }
    UUID id = e.player().uuid();
    PHASE_TRIGGERS.values().removeIf((Long at) -> now - at >= PHASE_TRIGGER_MILLIS);
    return PHASE_TRIGGERS.putIfAbsent(id, now) == null;
  }

  private static void remember(PlayerRef player, Loc destination, Kind kind) {
    long now = System.currentTimeMillis();
    BLINKS.values().removeIf((Blink blink) -> now - blink.at() > BLINK_MEMORY_MILLIS);
    BLINKS.put(player.uuid(), new Blink(destination, now, kind));
  }

  private static Kind kind(PlayerTeleportEvent e) {
    if (e.cause() != TeleportCause.PLUGIN) {
      return null;
    }
    Blink blink = BLINKS.get(e.player().uuid());
    boolean matches = blink != null && System.currentTimeMillis() - blink.at() <= BLINK_MEMORY_MILLIS
        && blink.destination().sameWorld(e.to()) && blink.destination().distanceSquared(e.to()) <= SAME_SPOT_SQUARED;
    return matches ? blink.kind() : null;
  }

  private record Blink(Loc destination, long at, Kind kind) {
  }
}
