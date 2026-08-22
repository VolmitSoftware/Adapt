package art.arcane.adapt.util.common.inventorygui;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Shared across every Adapt GUI so a suppression raised by one is honoured by another's close handler. */
public final class GuiCloseSuppression {
  public static final long SUPPRESS_MS = 1200L;
  private static final Map<UUID, Long> SUPPRESS_UNTIL = new ConcurrentHashMap<>();

  private GuiCloseSuppression() {
  }

  public static void suppress(Player player) {
    if (player == null) {
      return;
    }

    suppress(player.getUniqueId(), System.currentTimeMillis());
  }

  public static boolean consume(Player player) {
    if (player == null) {
      return false;
    }

    return consume(player.getUniqueId(), System.currentTimeMillis());
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
