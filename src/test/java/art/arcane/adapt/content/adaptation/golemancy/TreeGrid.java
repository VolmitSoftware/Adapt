package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class TreeGrid implements TreeView {
  private final Map<Long, Material> types = new HashMap<>();
  private final Set<Long> placed = new HashSet<>();
  private final Set<Long> unowned = new HashSet<>();
  private final Set<Long> decaying = new HashSet<>();
  private int reads;

  TreeGrid set(int x, int y, int z, Material type) {
    types.put(key(x, y, z), type);
    return this;
  }

  TreeGrid column(int x, int z, int fromY, int toY, Material type) {
    for (int y = fromY; y <= toY; y++) {
      set(x, y, z, type);
    }
    return this;
  }

  TreeGrid ground(int fromX, int fromZ, int toX, int toZ, int y, Material type) {
    for (int x = fromX; x <= toX; x++) {
      for (int z = fromZ; z <= toZ; z++) {
        set(x, y, z, type);
      }
    }
    return this;
  }

  TreeGrid canopy(int centerX, int fromY, int toY, int centerZ, int radius, Material leaves) {
    for (int y = fromY; y <= toY; y++) {
      for (int x = centerX - radius; x <= centerX + radius; x++) {
        for (int z = centerZ - radius; z <= centerZ + radius; z++) {
          if (!types.containsKey(key(x, y, z))) {
            set(x, y, z, leaves);
          }
        }
      }
    }
    return this;
  }

  TreeGrid stamp(int x, int y, int z) {
    placed.add(key(x, y, z));
    return this;
  }

  TreeGrid foreign(int x, int y, int z) {
    unowned.add(key(x, y, z));
    return this;
  }

  TreeGrid loosen(int x, int y, int z) {
    decaying.add(key(x, y, z));
    return this;
  }

  int reads() {
    return reads;
  }

  @Override
  public Material type(int x, int y, int z) {
    reads++;
    return types.getOrDefault(key(x, y, z), Material.AIR);
  }

  @Override
  public boolean owned(int x, int y, int z) {
    return !unowned.contains(key(x, y, z));
  }

  @Override
  public boolean placed(int x, int y, int z) {
    return placed.contains(key(x, y, z));
  }

  @Override
  public boolean decaying(int x, int y, int z) {
    return decaying.contains(key(x, y, z));
  }

  private static long key(int x, int y, int z) {
    return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
  }
}
