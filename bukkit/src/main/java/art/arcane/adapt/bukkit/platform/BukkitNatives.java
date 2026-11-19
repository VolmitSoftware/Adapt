package art.arcane.adapt.bukkit.platform;

import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.volmlib.nativelib.player.PlayerClientAccess;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Native server capabilities shipped in this jar. The 26.x jar bundles VolmLib's native adapters for every server it
 * supports, so a missing capability there is a fault and throws; a jar for an older server carries its own
 * implementations for its one server version.
 */
public final class BukkitNatives {
  private BukkitNatives() {
  }

  public static <T> Optional<T> find(Class<T> capability) {
    return Optional.of(NativeAdapters.require(capability));
  }

  /** True for the server's own connected player class, false for fake and NPC player implementations. */
  public static boolean isServerPlayer(Player player) {
    return PlayerClientAccess.matchesServerPlayer(player);
  }
}
