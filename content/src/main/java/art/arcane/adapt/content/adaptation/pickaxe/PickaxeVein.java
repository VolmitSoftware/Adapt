package art.arcane.adapt.content.adaptation.pickaxe;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.item.Tools;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static art.arcane.adapt.api.world.BlockFlag.VEIN_MINED;

/** Finds and breaks a connected ore vein for the adaptation that owns the break. */
public final class PickaxeVein {
  private static final String DEEPSLATE_PREFIX = "deepslate_";
  private static final Id FORGE_ORES = Id.parse("forge:ores");
  private static final Id COMMON_ORES = Id.parse("c:ores");

  private PickaxeVein() {
  }

  public static Id veinFamily(Id material) {
    String name = material.path();
    if (name.startsWith(DEEPSLATE_PREFIX) && name.endsWith("_ore")) {
      return material.withPath(name.substring(DEEPSLATE_PREFIX.length()));
    }
    return material;
  }

  public static boolean isVeinminable(Id material) {
    return isOre(material) || material.equals(Id.minecraft("obsidian"));
  }

  /** Any ore block, deepslate and nether ores included, ancient debris, and modded blocks in the loader ore tags. */
  public static boolean isOre(Id material) {
    if (material.path().endsWith("_ore") || material.equals(Id.minecraft("ancient_debris"))) {
      return true;
    }
    return !material.isMinecraft() && (Tools.isBlockTagged(material, FORGE_ORES) || Tools.isBlockTagged(material, COMMON_ORES));
  }

  /** The blocks of {@code origin}'s vein within {@code radius}, without {@code origin} itself. */
  public static List<BlockRef> siblings(Adaptation<?> owner, PlayerRef player, BlockRef origin, int radius, int maxBlocks) {
    Id targetFamily = veinFamily(origin.type());
    Set<BlockRef> blockMap = new HashSet<>();
    Set<BlockRef> queued = new HashSet<>();
    Deque<BlockRef> queue = new ArrayDeque<>();
    queue.add(origin);
    queued.add(origin);
    int radiusSquared = radius * radius;
    int limit = Math.max(1, maxBlocks);
    while (!queue.isEmpty() && blockMap.size() < limit) {
      BlockRef current = queue.poll();
      if (current == null) {
        continue;
      }

      if ((AdaptApi.scheduler().isRegionized() && !AdaptApi.scheduler().owns(current.location()))
          || !veinFamily(current.type()).equals(targetFamily)
          || blockMap.contains(current)) {
        continue;
      }

      if (distanceSquared(current, origin) > radiusSquared || !owner.canBlockBreak(player, current.location())) {
        continue;
      }

      blockMap.add(current);
      for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
          for (int z = -1; z <= 1; z++) {
            if (x == 0 && y == 0 && z == 0) {
              continue;
            }

            BlockRef next = current.relative(x, y, z);
            if ((AdaptApi.scheduler().isRegionized() && !AdaptApi.scheduler().owns(next.location()))
                || !veinFamily(next.type()).equals(targetFamily)) {
              continue;
            }

            if (distanceSquared(next, origin) > radiusSquared) {
              continue;
            }

            if (queued.add(next)) {
              queue.add(next);
            }
          }
        }
      }
    }

    List<BlockRef> siblings = new ArrayList<>(Math.max(0, blockMap.size() - 1));
    for (BlockRef candidate : blockMap) {
      if (!candidate.equals(origin)) {
        siblings.add(candidate);
      }
    }
    return siblings;
  }

  /**
   * Breaks the siblings once the origin is gone, a tick after it broke. Returns the blocks broken including the origin,
   * or 0 when the player, tool or origin no longer allow the vein.
   */
  public static int breakSiblings(Adaptation<?> owner, PlayerRef player, BlockRef origin, Id targetFamily,
                                  List<BlockRef> siblings, Predicate<Id> accepts, Crumble crumble) {
    if (AdaptApi.players().find(player).isEmpty()
        || owner.getActiveLevel(player) <= 0
        || !player.isOnline()
        || !player.world().uid().equals(origin.world().uid())
        || !ItemKinds.isPickaxe(player.inventory().mainHand())
        || (AdaptApi.scheduler().isRegionized()
        && (!AdaptApi.scheduler().owns(player) || !AdaptApi.scheduler().owns(origin.location())))
        || veinFamily(origin.type()).equals(targetFamily)) {
      return 0;
    }

    int successful = 1;
    for (BlockRef sibling : siblings) {
      Loc location = sibling.location();
      if (!player.world().uid().equals(sibling.world().uid())
          || (AdaptApi.scheduler().isRegionized() && !AdaptApi.scheduler().owns(location))
          || !veinFamily(sibling.type()).equals(targetFamily)
          || !accepts.test(sibling.type())
          || !owner.canBlockBreak(player, location)) {
        continue;
      }

      VEIN_MINED.add(sibling);
      try {
        BlockStateRef blockData = sibling.state();
        if (ProtectionProbes.attemptBlockBreak(player, sibling)) {
          crumble.broke(sibling, blockData, successful);
          successful++;
        }
      } finally {
        VEIN_MINED.remove(sibling);
      }
    }
    return successful;
  }

  /** Sees each sibling the vein broke, with its state before the break and the blocks broken before it. */
  public interface Crumble {
    void broke(BlockRef block, BlockStateRef state, int broken);
  }

  private static int distanceSquared(BlockRef first, BlockRef second) {
    int dx = first.x() - second.x();
    int dy = first.y() - second.y();
    int dz = first.z() - second.z();
    return (dx * dx) + (dy * dy) + (dz * dz);
  }
}
