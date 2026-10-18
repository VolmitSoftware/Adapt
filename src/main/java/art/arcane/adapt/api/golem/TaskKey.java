package art.arcane.adapt.api.golem;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.UUID;

final class TaskKey {
  private final String type;
  private final GolemTarget.Kind kind;
  private final long block;
  private final UUID entity;
  private final ItemStack item;
  private final int hash;

  private TaskKey(String type, GolemTarget.Kind kind, long block, UUID entity, ItemStack item) {
    this.type = type;
    this.kind = kind;
    this.block = block;
    this.entity = entity;
    this.item = item;
    this.hash = 31 * (31 * (31 * (31 * type.hashCode() + kind.hashCode()) + Long.hashCode(block)) + Objects.hashCode(entity))
        + Objects.hashCode(item);
  }

  static TaskKey of(String type, GolemTarget target, ItemStack item) {
    ItemStack single = single(item);
    return target.kind() == GolemTarget.Kind.BLOCK
        ? new TaskKey(type, target.kind(), blockKey(target.blockX(), target.blockY(), target.blockZ()), null, single)
        : new TaskKey(type, target.kind(), 0L, target.entity(), single);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof TaskKey key && hash == key.hash && block == key.block && kind == key.kind && type.equals(key.type)
        && Objects.equals(entity, key.entity) && Objects.equals(item, key.item);
  }

  @Override
  public int hashCode() {
    return hash;
  }

  private static ItemStack single(ItemStack item) {
    if (item == null || item.getAmount() == 1) {
      return item;
    }
    ItemStack single = item.clone();
    single.setAmount(1);
    return single;
  }

  private static long blockKey(int x, int y, int z) {
    return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
  }
}
