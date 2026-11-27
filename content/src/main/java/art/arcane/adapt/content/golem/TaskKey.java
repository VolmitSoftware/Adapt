package art.arcane.adapt.content.golem;

import art.arcane.adapt.platform.item.ItemRef;

import java.util.Objects;
import java.util.UUID;

final class TaskKey {
  private final String type;
  private final GolemTarget.Kind kind;
  private final long block;
  private final UUID entity;
  private final ItemRef item;
  private final int hash;

  private TaskKey(String type, GolemTarget.Kind kind, long block, UUID entity, ItemRef item) {
    this.type = type;
    this.kind = kind;
    this.block = block;
    this.entity = entity;
    this.item = item;
    this.hash = 31 * (31 * (31 * (31 * type.hashCode() + kind.hashCode()) + Long.hashCode(block)) + Objects.hashCode(entity))
        + (item == null ? 0 : item.type().hashCode());
  }

  static TaskKey of(String type, GolemTarget target, ItemRef item) {
    ItemRef single = single(item);
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
        && Objects.equals(entity, key.entity) && sameItem(item, key.item);
  }

  @Override
  public int hashCode() {
    return hash;
  }

  private static boolean sameItem(ItemRef first, ItemRef second) {
    return first == null ? second == null : second != null && first.isSimilar(second);
  }

  private static ItemRef single(ItemRef item) {
    if (item == null || item.amount() == 1) {
      return item;
    }
    return item.copyWithAmount(1);
  }

  private static long blockKey(int x, int y, int z) {
    return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
  }
}
