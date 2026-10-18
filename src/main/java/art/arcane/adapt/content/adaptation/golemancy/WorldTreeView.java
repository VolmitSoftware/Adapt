package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Leaves;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

final class WorldTreeView implements TreeView {
  private final World world;
  private final Predicate<Block> owns;
  private final Predicate<Block> placed;
  private final Map<Long, Boolean> chunks = new HashMap<>(4);

  WorldTreeView(World world, Predicate<Block> owns, Predicate<Block> placed) {
    this.world = Objects.requireNonNull(world);
    this.owns = Objects.requireNonNull(owns);
    this.placed = Objects.requireNonNull(placed);
  }

  @Override
  public Material type(int x, int y, int z) {
    return world.getBlockAt(x, y, z).getType();
  }

  @Override
  public boolean owned(int x, int y, int z) {
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

  @Override
  public boolean placed(int x, int y, int z) {
    return placed.test(world.getBlockAt(x, y, z));
  }

  @Override
  public boolean persistent(int x, int y, int z) {
    return world.getBlockAt(x, y, z).getBlockData() instanceof Leaves leaves && leaves.isPersistent();
  }

  @Override
  public boolean decaying(int x, int y, int z) {
    return world.getBlockAt(x, y, z).getBlockData() instanceof Leaves leaves && leaves.getDistance() >= leaves.getMaximumDistance();
  }
}
