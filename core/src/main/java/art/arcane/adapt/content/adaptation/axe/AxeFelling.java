package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static art.arcane.adapt.util.data.BlockFlag.VEIN_MINED;

/** Tree felling shared by Axe Chop, the wood and leaf veinminers and Timberfall. */
public final class AxeFelling {
  private static final Set<Id> LOG_LIKE = Set.of(
      Id.minecraft("mushroom_stem"),
      Id.minecraft("brown_mushroom_block"),
      Id.minecraft("red_mushroom_block"),
      Id.minecraft("mangrove_roots"),
      Id.minecraft("muddy_mangrove_roots"));
  private static final Id MANGROVE_ROOTS = Id.minecraft("mangrove_roots");
  private static final Id MUDDY_MANGROVE_ROOTS = Id.minecraft("muddy_mangrove_roots");

  private AxeFelling() {
  }

  /** Logs, wood, stems and the other blocks the wood veinminer fells. */
  public static boolean isWood(Id type) {
    String name = type.path();
    return LOG_LIKE.contains(type)
        || name.endsWith("_log")
        || name.endsWith("_wood");
  }

  /** Leaves and the mangrove roots the leaf veinminer clears. */
  public static boolean isFoliage(Id type) {
    return type.equals(MANGROVE_ROOTS)
        || type.equals(MUDDY_MANGROVE_ROOTS)
        || type.path().endsWith("_leaves");
  }

  /** The highest log in the unbroken log column that starts at {@code base}, climbing at most {@code power} blocks. */
  public static BlockRef trunkTop(Adaptation<?> owner, BlockRef base, int power) {
    BlockRef last = base;
    for (int i = base.y(); i < power + base.y(); i++) {
      BlockRef bb = base.world().blockAt(base.x(), i, base.z());
      if (owner.isLog(bb.type())) {
        last = bb;
      } else {
        break;
      }
    }
    return last;
  }

  /** Breaks the top log of the column at {@code base} as the player; true when it broke. */
  public static boolean chopTop(Adaptation<?> owner, BlockRef base, int power, PlayerRef player) {
    BlockRef last = trunkTop(owner, base, power);
    if (!owner.canBlockBreak(player, last.location())) {
      return false;
    }

    if (!owner.isLog(last.type())) {
      return false;
    }

    if (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(last.location())) {
      return false;
    }
    VEIN_MINED.add(last);
    try {
      return AdaptCore.platform().protection().attemptBreak(player, last);
    } finally {
      VEIN_MINED.remove(last);
    }
  }

  /** Blocks of the origin's type within {@code radius}, scanned shell by shell, up to {@code maxBlocks}. */
  public static Set<BlockRef> scanWood(Adaptation<?> owner, PlayerRef player, BlockRef origin, int radius, int maxBlocks) {
    Id blockType = origin.type();
    Set<BlockRef> blockMap = new HashSet<>(maxBlocks);
    int radiusSquared = radius * radius;
    scan:
    for (int i = 0; i < radius; i++) {
      for (int x = -i; x <= i; x++) {
        for (int y = -i; y <= i; y++) {
          for (int z = -i; z <= i; z++) {
            if (blockMap.size() >= maxBlocks) {
              break scan;
            }
            BlockRef b = origin.relative(x, y, z);
            if ((!Tasks.isFoliaThreading() || Tasks.isOwnedByCurrentRegion(b.location()))
                && b.type().equals(blockType)) {
              if ((x * x) + (y * y) + (z * z) > radiusSquared) {
                continue;
              }
              if (!owner.canBlockBreak(player, b.location())) {
                continue;
              }
              blockMap.add(b);
            }
          }
        }
      }
    }
    return blockMap;
  }

  /** Touching blocks of the origin's type within {@code radius} of it, flood-filled up to {@code maxBlocks}. */
  public static Set<BlockRef> floodFoliage(Adaptation<?> owner, PlayerRef player, BlockRef origin, int radius, int maxBlocks) {
    Id blockType = origin.type();
    Set<BlockRef> blockMap = new HashSet<>();
    Deque<BlockRef> stack = new ArrayDeque<>();
    Set<BlockRef> queued = new HashSet<>();
    stack.push(origin);
    queued.add(origin);
    int radiusSquared = radius * radius;
    while (!stack.isEmpty() && blockMap.size() < maxBlocks) {
      BlockRef currentBlock = stack.pop();
      if (blockMap.contains(currentBlock)) {
        continue;
      }
      blockMap.add(currentBlock);
      for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
          for (int z = -1; z <= 1; z++) {
            if (x == 0 && y == 0 && z == 0) {
              continue;
            }
            BlockRef b = currentBlock.relative(x, y, z);
            if ((Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(b.location()))
                || !b.type().equals(blockType)
                || blockMap.contains(b)
                || !queued.add(b)) {
              continue;
            }
            if (distanceSquared(origin, b) <= radiusSquared && owner.canBlockBreak(player, b.location())) {
              stack.push(b);
            } else {
              queued.remove(b);
            }
          }
        }
      }
    }
    return blockMap;
  }

  /**
   * Breaks every target that still has {@code blockType} as the player, flagging each as vein mined while it breaks,
   * and clears the origin's flag at the end. The returned list starts with the origin.
   */
  public static List<BlockRef> breakAll(Adaptation<?> owner, PlayerRef player, BlockRef origin, Id blockType,
                                        Set<BlockRef> targets) {
    List<BlockRef> brokenBlocks = new ArrayList<>(targets.size() + 1);
    brokenBlocks.add(origin);
    try {
      for (BlockRef target : targets) {
        Loc location = target.location();
        if ((Tasks.isFoliaThreading()
            && (!Tasks.isOwnedByCurrentRegion(player) || !Tasks.isOwnedByCurrentRegion(location)))
            || !target.type().equals(blockType)
            || !owner.canBlockBreak(player, location)) {
          continue;
        }

        VEIN_MINED.add(target);
        try {
          if (ProtectionProbes.attemptBlockBreak(player, target)) {
            brokenBlocks.add(target);
          }
        } finally {
          VEIN_MINED.remove(target);
        }
      }
    } finally {
      VEIN_MINED.remove(origin);
    }
    return brokenBlocks;
  }

  private static int distanceSquared(BlockRef first, BlockRef second) {
    int dx = first.x() - second.x();
    int dy = first.y() - second.y();
    int dz = first.z() - second.z();
    return (dx * dx) + (dy * dy) + (dz * dz);
  }
}
