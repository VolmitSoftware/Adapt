package art.arcane.adapt.util.common.inventorygui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The {@link GuiWindow} each player opened last, so a config hotload can rebuild it from its tag. */
public final class GuiSessions {
  private static final Map<UUID, GuiWindow> OPEN = new ConcurrentHashMap<>();

  private GuiSessions() {
  }

  public static void track(GuiWindow window) {
    if (window != null) {
      OPEN.put(window.getViewer().uuid(), window);
    }
  }

  public static void untrack(UUID playerId, GuiWindow window) {
    if (playerId != null && window != null) {
      OPEN.remove(playerId, window);
    }
  }

  public static Map<UUID, GuiWindow> snapshot() {
    return Map.copyOf(OPEN);
  }

  public static void clear() {
    OPEN.clear();
  }
}
