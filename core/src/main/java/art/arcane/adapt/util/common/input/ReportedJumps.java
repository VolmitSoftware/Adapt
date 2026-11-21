package art.arcane.adapt.util.common.input;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The jump key of players whose Adapt client reports it ({@code JumpKey} frames), on servers that receive no player
 * input packet. Reports arrive in packet order with the player's moves. Presses are counted, so each reader sees a tap
 * that started and ended between two of its reads.
 */
public final class ReportedJumps {
  private static final Map<UUID, Key> KEYS = new ConcurrentHashMap<>();

  private ReportedJumps() {
  }

  /** Starts or ends taking the player's reports; ending forgets the key. */
  public static void reporting(UUID playerId, boolean reporting) {
    if (reporting) {
      KEYS.putIfAbsent(playerId, new Key());
    } else {
      KEYS.remove(playerId);
    }
  }

  /** True while the player's client reports the jump key. */
  public static boolean reporting(UUID playerId) {
    return KEYS.containsKey(playerId);
  }

  /** A report from the player's client; ignored unless the player reports. */
  public static void report(UUID playerId, boolean down) {
    Key key = KEYS.get(playerId);
    if (key == null) {
      return;
    }
    synchronized (key) {
      if (down && !key.down) {
        key.presses++;
      }
      key.down = down;
    }
  }

  /** True while the reported key is down. */
  public static boolean down(UUID playerId) {
    Key key = KEYS.get(playerId);
    return key != null && key.down;
  }

  /** How many times the reported key went down since reporting started. */
  public static long presses(UUID playerId) {
    Key key = KEYS.get(playerId);
    return key == null ? 0L : key.presses;
  }

  private static final class Key {
    private volatile boolean down;
    private volatile long presses;
  }
}
