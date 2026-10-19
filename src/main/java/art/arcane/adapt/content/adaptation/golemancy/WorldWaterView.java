package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

final class WorldWaterView implements AnglerWater.View {
  private final World world;
  private final Predicate<Block> owns;
  private final Map<Long, Boolean> chunks = new HashMap<>(4);

  WorldWaterView(World world, Predicate<Block> owns) {
    this.world = Objects.requireNonNull(world);
    this.owns = Objects.requireNonNull(owns);
  }

  @Override
  public AnglerWater.Cell cell(int x, int y, int z) {
    if (!owned(x, y, z)) {
      return AnglerWater.Cell.OTHER;
    }
    Block block = world.getBlockAt(x, y, z);
    Material type = block.getType();
    return AnglerWater.cell(type, type == Material.WATER ? block.getBlockData() : null);
  }

  private boolean owned(int x, int y, int z) {
    int chunkX = x >> 4;
    int chunkZ = z >> 4;
    long chunk = (long) chunkX << 32 | (chunkZ & 0xFFFFFFFFL);
    Boolean known = chunks.get(chunk);
    if (known != null) {
      return known;
    }
    boolean owned = world.isChunkLoaded(chunkX, chunkZ) && owns.test(world.getBlockAt(x, y, z));
    chunks.put(chunk, owned);
    return owned;
  }
}
