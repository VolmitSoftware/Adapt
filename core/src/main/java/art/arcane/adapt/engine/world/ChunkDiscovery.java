package art.arcane.adapt.engine.world;

import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.UUID;

/**
 * Chunks a player has entered, one 32x32-chunk region per entry holding a 1024-bit set (128 bytes, Base64 in the
 * player file). The oldest regions are dropped past {@link #REGION_CAP}, which bounds the data at about 180 KB.
 */
public class ChunkDiscovery {
  public static final int REGION_CAP = 1024;
  private static final int REGION_SHIFT = 5;
  private static final int REGION_BYTES = 128;

  private final LinkedHashMap<String, String> regions = new LinkedHashMap<>();

  public synchronized boolean isNewDiscovery(UUID world, int chunkX, int chunkZ) {
    String key = world + ":" + (chunkX >> REGION_SHIFT) + ":" + (chunkZ >> REGION_SHIFT);
    String stored = regions.remove(key);
    byte[] bits = stored == null ? new byte[REGION_BYTES] : Base64.getDecoder().decode(stored);
    int index = ((chunkZ & 31) << REGION_SHIFT) | (chunkX & 31);
    int mask = 1 << (index & 7);
    boolean fresh = (bits[index >> 3] & mask) == 0;
    bits[index >> 3] = (byte) (bits[index >> 3] | mask);
    regions.put(key, Base64.getEncoder().encodeToString(bits));
    trim();
    return fresh;
  }

  public synchronized int regionCount() {
    return regions.size();
  }

  private void trim() {
    Iterator<String> oldest = regions.keySet().iterator();
    while (regions.size() > REGION_CAP && oldest.hasNext()) {
      oldest.next();
      oldest.remove();
    }
  }
}
