package art.arcane.adapt.platform.value;

import org.jetbrains.annotations.ApiStatus;

/** Chunk coordinate. {@link #key()} matches the Bukkit and vanilla packed chunk key layout. */
@ApiStatus.AvailableSince("1.0.0")
public record ChunkPos(int x, int z) {
  /** This chunk packed into a long: Z in the high 32 bits, X in the low 32 bits. */
  public long key() {
    return key(x, z);
  }

  /** Packs chunk coordinates the same way as {@link #key()}. */
  public static long key(int chunkX, int chunkZ) {
    return ((long) chunkZ << 32) | (chunkX & 0xFFFFFFFFL);
  }

  /** Unpacks a key made by {@link #key()}. */
  public static ChunkPos fromKey(long key) {
    return new ChunkPos((int) key, (int) (key >>> 32));
  }

  /** The chunk that contains the block coordinates. */
  public static ChunkPos containing(double blockX, double blockZ) {
    return new ChunkPos((int) Math.floor(blockX) >> 4, (int) Math.floor(blockZ) >> 4);
  }
}
