package art.arcane.adapt.bukkit.platform.entity;

import art.arcane.adapt.util.common.compat.PaperApi;
import net.kyori.adventure.util.TriState;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flight request grants ({@link art.arcane.adapt.platform.entity.PlayerRef#grantFlightRequests()}): may-fly on with
 * flying fall damage forced on, so the vanilla rule that players who may fly take no fall damage does not apply.
 * Keeps the fall damage setting the player had to put it back.
 */
final class BukkitFlightRequests {
  private static final Map<UUID, TriState> GRANTS = new ConcurrentHashMap<>();

  private BukkitFlightRequests() {
  }

  static boolean granted(Player player) {
    return GRANTS.containsKey(player.getUniqueId());
  }

  static boolean grant(Player player) {
    if (granted(player) || player.getAllowFlight() || !survivalLike(player)) {
      return false;
    }
    GRANTS.put(player.getUniqueId(), PaperApi.flyingFallDamage(player));
    PaperApi.setFlyingFallDamage(player, TriState.TRUE);
    player.setAllowFlight(true);
    return true;
  }

  static void revoke(Player player) {
    if (!release(player)) {
      return;
    }
    if (survivalLike(player)) {
      player.setAllowFlight(false);
    }
  }

  /** Drops the grant without touching may-fly, which its new owner sets next. True when there was one. */
  static boolean release(Player player) {
    TriState fallDamage = GRANTS.remove(player.getUniqueId());
    if (fallDamage == null) {
      return false;
    }
    if (PaperApi.flyingFallDamage(player) == TriState.TRUE) {
      PaperApi.setFlyingFallDamage(player, fallDamage);
    }
    return true;
  }

  private static boolean survivalLike(Player player) {
    GameMode mode = player.getGameMode();
    return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
  }
}
