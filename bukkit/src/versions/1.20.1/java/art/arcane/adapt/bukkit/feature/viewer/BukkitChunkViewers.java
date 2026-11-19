package art.arcane.adapt.bukkit.feature.viewer;

import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Players the server sends a chunk to. Paper 1.20.1 has no chunk tracking query, so this takes the players of the
 * world whose send view distance square covers the chunk.
 */
final class BukkitChunkViewers {
  private BukkitChunkViewers() {
  }

  static Collection<Player> seeing(World world, int chunkX, int chunkZ) {
    List<Player> seeing = new ArrayList<>();
    for (Player player : world.getPlayers()) {
      int distance = player.getSendViewDistance();
      int playerChunkX = player.getLocation().getBlockX() >> 4;
      int playerChunkZ = player.getLocation().getBlockZ() >> 4;
      if (Math.abs(playerChunkX - chunkX) <= distance && Math.abs(playerChunkZ - chunkZ) <= distance) {
        seeing.add(player);
      }
    }
    return seeing;
  }
}
