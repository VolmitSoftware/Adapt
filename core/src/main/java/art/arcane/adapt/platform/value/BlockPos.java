package art.arcane.adapt.platform.value;

/** Immutable integer block coordinate. */
public record BlockPos(int x, int y, int z) {
  public BlockPos offset(int dx, int dy, int dz) {
    return new BlockPos(x + dx, y + dy, z + dz);
  }

  public BlockPos relative(BlockFace face) {
    return new BlockPos(x + face.dx(), y + face.dy(), z + face.dz());
  }

  public BlockPos relative(BlockFace face, int distance) {
    return new BlockPos(x + (face.dx() * distance), y + (face.dy() * distance), z + (face.dz() * distance));
  }

  public int chunkX() {
    return x >> 4;
  }

  public int chunkZ() {
    return z >> 4;
  }

  public ChunkPos chunk() {
    return new ChunkPos(x >> 4, z >> 4);
  }

  public Vec3 center() {
    return new Vec3(x + 0.5D, y + 0.5D, z + 0.5D);
  }

  public Vec3 corner() {
    return new Vec3(x, y, z);
  }

  public long distanceSquared(BlockPos other) {
    long dx = (long) x - other.x;
    long dy = (long) y - other.y;
    long dz = (long) z - other.z;
    return (dx * dx) + (dy * dy) + (dz * dz);
  }

  /** Packs into a long using the vanilla 26-12-26 bit layout. */
  public long asLong() {
    return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (long) (y & 0xFFF);
  }

  public static BlockPos fromLong(long packed) {
    int x = (int) (packed >> 38);
    int y = (int) (packed << 52 >> 52);
    int z = (int) (packed << 26 >> 38);
    return new BlockPos(x, y, z);
  }
}
