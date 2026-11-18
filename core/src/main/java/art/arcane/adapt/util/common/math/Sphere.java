package art.arcane.adapt.util.common.math;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.adapt.platform.value.BlockPos;

import java.util.Iterator;

public class Sphere implements Iterator<BlockPos>, Cloneable {
  private final KList<BlockPos> blocks;
  private int i = 0;

  public Sphere(int radius) {
    int dist = radius * radius * radius;

    blocks = new KList<>();
    for (int x = -radius; x <= radius; x++) {
      for (int z = -radius; z <= radius; z++) {
        for (int y = -radius; y <= radius; y++) {
          if (x * x + z * z + y * y > dist)
            continue;

          blocks.add(new BlockPos(x, y, z));
        }
      }
    }
  }

  private Sphere(KList<BlockPos> blocks) {
    this.blocks = blocks.copy();
  }

  public void reset() {
    i = 0;
  }

  @Override
  public boolean hasNext() {
    return i < blocks.size();
  }

  @Override
  public BlockPos next() {
    return blocks.get(i++);
  }

  @Override
  public Sphere clone() {
    return new Sphere(blocks);
  }
}
