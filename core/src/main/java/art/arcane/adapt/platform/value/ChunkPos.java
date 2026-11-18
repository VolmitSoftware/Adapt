package art.arcane.adapt.platform.value;

/** Chunk coordinate. {@link #key()} matches the Bukkit and vanilla packed chunk key layout. */
public record ChunkPos(int x, int z) {
  public long key() {
    return key(x, z);
  }

  public static long key(int chunkX, int chunkZ) {
    return ((long) chunkZ << 32) | (chunkX & 0xFFFFFFFFL);
  }

  public static ChunkPos fromKey(long key) {
    return new ChunkPos((int) key, (int) (key >>> 32));
  }

  public static ChunkPos containing(double blockX, double blockZ) {
    return new ChunkPos((int) Math.floor(blockX) >> 4, (int) Math.floor(blockZ) >> 4);
  }
}
