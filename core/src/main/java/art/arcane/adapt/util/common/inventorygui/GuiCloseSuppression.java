package art.arcane.adapt.util.common.inventorygui;

import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Shared across every Adapt GUI so a suppression raised by one is honoured by another's close handler. */
public final class GuiCloseSuppression {
  public static final long SUPPRESS_MS = 1200L;
  private static final Map<UUID, Long> SUPPRESS_UNTIL = new ConcurrentHashMap<>();

  private GuiCloseSuppression() {
  }

  public static void suppress(PlayerRef player) {
    if (player == null) {
      return;
    }

    suppress(player.uuid());
  }

  public static boolean consume(PlayerRef player) {
    if (player == null) {
      return false;
    }

    return consume(player.uuid());
  }

  public static void suppress(UUID playerId) {
    suppress(playerId, System.currentTimeMillis());
  }

  public static boolean consume(UUID playerId) {
    return consume(playerId, System.currentTimeMillis());
  }

  static void suppress(UUID playerId, long nowMs) {
    if (playerId == null) {
      return;
    }

    SUPPRESS_UNTIL.put(playerId, nowMs + SUPPRESS_MS);
  }

  static boolean consume(UUID playerId, long nowMs) {
    if (playerId == null) {
      return false;
    }

    Long until = SUPPRESS_UNTIL.remove(playerId);
    return until != null && until >= nowMs;
  }
}
