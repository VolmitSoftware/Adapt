package art.arcane.adapt.bukkit.feature.viewer;

import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;

/** Players the server sends a chunk to. */
final class BukkitChunkViewers {
  private BukkitChunkViewers() {
  }

  static Collection<Player> seeing(World world, int chunkX, int chunkZ) {
    return world.getPlayersSeeingChunk(chunkX, chunkZ);
  }
}
