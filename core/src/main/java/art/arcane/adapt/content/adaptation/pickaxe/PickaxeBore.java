package art.arcane.adapt.content.adaptation.pickaxe;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.BonusBlockBreaks;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.ObjIntConsumer;
import java.util.function.Predicate;

/** Collects and breaks the plane of blocks around a mined block, oriented by the miner's facing. */
public final class PickaxeBore {
  public static final Set<Id> STONE = Set.of(
      Id.minecraft("stone"), Id.minecraft("cobblestone"), Id.minecraft("mossy_cobblestone"),
      Id.minecraft("deepslate"), Id.minecraft("cobbled_deepslate"), Id.minecraft("tuff"),
      Id.minecraft("calcite"), Id.minecraft("andesite"), Id.minecraft("diorite"), Id.minecraft("granite"));
  private static final int[] SINGLE = {0};
  private static final int[] PAIR = {0, 1};
  private static final int[] TRIPLE = {-1, 0, 1};

  private PickaxeBore() {
  }

  /** A width of 1 or 3, centred, and a height of 2 (upward) or 3 (centred); the origin is left out. */
  public static List<BlockRef> plane(Adaptation<?> owner, PlayerRef player, BlockRef origin, int width, int height,
                                     Predicate<Id> boreable) {
    List<BlockRef> targets = new ArrayList<>(8);
    int[] wOffsets = width == 1 ? SINGLE : TRIPLE;
    int[] hOffsets = height == 2 ? PAIR : TRIPLE;
    Loc location = player.location();
    BlockFace facing = facing(location.yaw());
    float pitch = location.pitch();
    boolean steep = pitch > 60F || pitch < -60F;
    int fx = facing.dx();
    int fz = facing.dz();
    boolean widthOnX = fz != 0;
    for (int h : hOffsets) {
      for (int w : wOffsets) {
        if (h == 0 && w == 0) {
          continue;
        }

        BlockRef b;
        if (steep) {
          b = origin.relative((h * fx) - (w * fz), 0, (h * fz) + (w * fx));
        } else {
          b = origin.relative(widthOnX ? w : 0, h, widthOnX ? 0 : w);
        }

        Loc blockLocation = b.location();
        if ((Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(blockLocation))
            || !boreable.test(b.type())) {
          continue;
        }

        if (!owner.canBlockBreak(player, blockLocation)) {
          continue;
        }

        targets.add(b);
      }
    }

    return targets;
  }

  /** Whether the bore can go ahead a tick after the origin broke: the origin is gone and the player still mines. */
  public static boolean ready(Adaptation<?> owner, PlayerRef player, BlockRef origin, Id originType) {
    return player.isOnline()
        && player.world().uid().equals(origin.world().uid())
        && (!Tasks.isFoliaThreading()
        || (Tasks.isOwnedByCurrentRegion(player) && Tasks.isOwnedByCurrentRegion(origin.location())))
        && !origin.type().equals(originType)
        && owner.isPickaxe(player.inventory().mainHand())
        && owner.getActiveLevel(player) > 0;
  }

  /**
   * Breaks the accepted targets while a pickaxe stays in hand; {@code crumble} sees each target, with its index, just
   * before it breaks. Returns the blocks broken.
   */
  public static int breakTargets(Adaptation<?> owner, PlayerRef player, List<BlockRef> targets, Predicate<Id> accepts,
                                 int durabilityPerBlock, ObjIntConsumer<BlockRef> crumble) {
    int index = 0;
    int broken = 0;
    for (BlockRef target : targets) {
      if (!owner.isPickaxe(player.inventory().mainHand())) {
        break;
      }
      Loc location = target.location();
      if ((Tasks.isFoliaThreading()
          && (!Tasks.isOwnedByCurrentRegion(player) || !Tasks.isOwnedByCurrentRegion(location)))
          || !accepts.test(target.type())
          || !owner.canBlockBreak(player, location)) {
        index++;
        continue;
      }

      crumble.accept(target, index);
      if (BonusBlockBreaks.breakBlock(player, target, durabilityPerBlock)) {
        broken++;
      }
      index++;
    }
    return broken;
  }

  public static BlockFace facing(float yaw) {
    return switch (((int) Math.floor((yaw / 90.0D) + 0.5D)) & 3) {
      case 0 -> BlockFace.SOUTH;
      case 1 -> BlockFace.WEST;
      case 2 -> BlockFace.NORTH;
      default -> BlockFace.EAST;
    };
  }
}
