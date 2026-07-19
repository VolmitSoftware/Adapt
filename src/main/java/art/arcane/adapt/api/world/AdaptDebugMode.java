package art.arcane.adapt.api.world;

import art.arcane.adapt.api.adaptation.PlayerStateRegistry;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

public final class AdaptDebugMode {
  private static final Map<UUID, Boolean> ACTIVE = PlayerStateRegistry.newPlayerMap();

  private AdaptDebugMode() {
  }

  public static boolean isActive(UUID uuid) {
    return uuid != null && ACTIVE.containsKey(uuid);
  }

  public static boolean isActive(Player player) {
    return player != null && isActive(player.getUniqueId());
  }

  public static void setActive(UUID uuid, boolean active) {
    if (uuid == null) {
      return;
    }

    if (active) {
      ACTIVE.put(uuid, Boolean.TRUE);
    } else {
      ACTIVE.remove(uuid);
    }
  }

  public static boolean toggle(UUID uuid) {
    boolean next = !isActive(uuid);
    setActive(uuid, next);
    return next;
  }
}
