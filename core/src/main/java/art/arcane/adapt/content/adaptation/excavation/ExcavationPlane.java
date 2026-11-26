package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.BonusBlockBreaks;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Breaks the ring of soft blocks around a dug block, in the plane the player faces: flat when looking steeply up or
 * down, otherwise upright across the view. The break runs one tick after the dig so the origin is gone first.
 */
public final class ExcavationPlane {
  private static final int[][] PLANE_OFFSETS = {
      {0, 1}, {0, -1}, {1, 0}, {-1, 0}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}
  };

  private final Adaptation<?> owner;
  private final BiConsumer<PlayerRef, List<BlockRef>> onCarved;

  /** {@code onCarved} runs on the player's thread with the bonus blocks broken. */
  public ExcavationPlane(Adaptation<?> owner, BiConsumer<PlayerRef, List<BlockRef>> onCarved) {
    this.owner = owner;
    this.onCarved = onCarved;
  }

  /** Schedules the plane around {@code origin}; false when no neighbour qualifies. */
  public boolean carve(PlayerRef p, BlockRef origin, int bonus, int durabilityCost, Predicate<Id> allowed) {
    Id originType = origin.type();
    List<int[]> targetOffsets = new ArrayList<>(PLANE_OFFSETS.length);
    for (int[] offset : ring(p)) {
      int dx = offset[0];
      int dy = offset[1];
      int dz = offset[2];
      BlockRef target = origin.relative(dx, dy, dz);
      Loc targetLocation = target.location();
      if ((Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(targetLocation))
          || !ExcavationBlocks.isShovelable(target.type())
          || !allowed.test(target.type())
          || !owner.canBlockBreak(p, targetLocation)) {
        continue;
      }

      targetOffsets.add(new int[]{dx, dy, dz});
    }

    if (targetOffsets.isEmpty()) {
      return false;
    }

    Tasks.runEntity(p, () -> breakPlane(p, origin, originType, bonus, durabilityCost, allowed, targetOffsets), 1);
    return true;
  }

  private void breakPlane(PlayerRef player, BlockRef origin, Id originType, int bonus, int durabilityCost,
                          Predicate<Id> allowed, List<int[]> targetOffsets) {
    Loc originLocation = origin.location();
    if (!player.isOnline()
        || !player.world().uid().equals(origin.world().uid())
        || (Tasks.isFoliaThreading()
        && (!Tasks.isOwnedByCurrentRegion(player) || !Tasks.isOwnedByCurrentRegion(originLocation)))
        || origin.type().equals(originType)) {
      return;
    }

    ItemRef hand = player.inventory().mainHand();
    if (!owner.isShovel(hand)) {
      return;
    }

    Loc originCenter = origin.center();
    List<int[]> sweepCells = new ArrayList<>(targetOffsets.size());
    List<BlockStateRef> sweepData = new ArrayList<>(targetOffsets.size());
    List<BlockRef> carved = new ArrayList<>(targetOffsets.size());
    boolean durabilityStop = false;
    for (int[] offset : targetOffsets) {
      if (carved.size() >= bonus) {
        break;
      }

      BlockRef target = origin.relative(offset[0], offset[1], offset[2]);
      Loc targetLocation = target.location();
      if ((Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(targetLocation))
          || !ExcavationBlocks.isShovelable(target.type())
          || !allowed.test(target.type())
          || !owner.canBlockBreak(player, targetLocation)) {
        continue;
      }

      if (!BonusBlockBreaks.canCharge(hand, durabilityCost)) {
        durabilityStop = true;
        break;
      }

      BlockStateRef blockData = target.state();
      if (BonusBlockBreaks.breakBlock(player, target, durabilityCost)) {
        sweepCells.add(offset);
        sweepData.add(blockData);
        carved.add(target);
      }
    }

    if (durabilityStop) {
      ExcavationTunneler.durabilityStopFx(owner, originCenter);
    }

    if (carved.isEmpty()) {
      return;
    }

    ExcavationTunneler.planeSweepFx(owner, originCenter, sweepCells, sweepData, carved.size());
    onCarved.accept(player, carved);
  }

  /** The eight {dx, dy, dz} offsets around a block in the plane the player faces. */
  public static List<int[]> ring(PlayerRef p) {
    float pitch = p.location().pitch();
    boolean horizontal = pitch >= 50 || pitch <= -50;
    boolean facingX = !horizontal && isFacingX(p.location().yaw());
    List<int[]> ring = new ArrayList<>(PLANE_OFFSETS.length);
    for (int[] offset : PLANE_OFFSETS) {
      if (horizontal) {
        ring.add(new int[]{offset[0], 0, offset[1]});
      } else if (facingX) {
        ring.add(new int[]{0, offset[1], offset[0]});
      } else {
        ring.add(new int[]{offset[0], offset[1], 0});
      }
    }
    return ring;
  }

  private static boolean isFacingX(float yaw) {
    float normalized = ((yaw % 360) + 360) % 360;
    return (normalized >= 45 && normalized < 135) || (normalized >= 225 && normalized < 315);
  }
}
