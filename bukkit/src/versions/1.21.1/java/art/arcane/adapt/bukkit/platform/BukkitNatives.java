package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.bukkit.natives.ServerNatives;
import org.bukkit.entity.Player;

import java.util.Optional;

/** Native server capabilities shipped in this jar: the per-version implementations in {@link ServerNatives}. */
public final class BukkitNatives {
  private BukkitNatives() {
  }

  public static <T> Optional<T> find(Class<T> capability) {
    return Optional.ofNullable(ServerNatives.find(capability));
  }

  /** True for the server's own connected player class, false for fake and NPC player implementations. */
  public static boolean isServerPlayer(Player player) {
    return ServerNatives.isServerPlayer(player);
  }
}
