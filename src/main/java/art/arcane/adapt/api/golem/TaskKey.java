package art.arcane.adapt.api.golem;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

record TaskKey(String type, GolemTarget.Kind kind, long block, UUID entity, ItemStack item) {
  static TaskKey of(String type, GolemTarget target, ItemStack item) {
    return target.kind() == GolemTarget.Kind.BLOCK
        ? new TaskKey(type, target.kind(), blockKey(target.blockX(), target.blockY(), target.blockZ()), null, item)
        : new TaskKey(type, target.kind(), 0L, target.entity(), item);
  }

  private static long blockKey(int x, int y, int z) {
    return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
  }
}
