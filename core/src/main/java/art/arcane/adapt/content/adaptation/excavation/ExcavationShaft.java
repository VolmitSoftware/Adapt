package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * A straight shaft dug down through soft ground, one block every few ticks, stopping above lava, open drops, hard
 * blocks and the safe floor margin. Each dug block costs shovel durability.
 */
public final class ExcavationShaft {
  private static final Id LAVA = Id.minecraft("lava");

  private final Adaptation<?> owner;
  private final BiConsumer<PlayerRef, BlockRef> onDug;

  /** {@code onDug} runs on the player's thread for every block the shaft removes. */
  public ExcavationShaft(Adaptation<?> owner, BiConsumer<PlayerRef, BlockRef> onDug) {
    this.owner = owner;
    this.onDug = onDug;
  }

  /** The blocks from {@code start} downward that the shaft may dig, at most {@code maxDepth}. */
  public List<BlockRef> plan(PlayerRef p, BlockRef start, int maxDepth, int safeFloorMargin) {
    WorldRef world = start.world();
    int stopY = world.minHeight() + safeFloorMargin;
    List<BlockRef> plan = new ArrayList<>(maxDepth);
    BlockRef current = start;
    for (int i = 0; i < maxDepth; i++) {
      if (current.y() <= stopY
          || (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(current.location()))) {
        break;
      }

      if (!ExcavationBlocks.isShovelable(current.type())) {
        break;
      }

      if (!owner.canBlockBreak(p, current.location())) {
        break;
      }

      BlockRef below = current.relative(BlockFace.DOWN);
      Id belowType = below.type();
      if (belowType.equals(LAVA)) {
        break;
      }

      if (belowType.isAir() && below.relative(BlockFace.DOWN).type().isAir()) {
        break;
      }

      plan.add(current);
      current = below;
    }

    return plan;
  }

  /** Digs every planned block after the first, one per {@code ticksPerBlock}, then plays the bottom-out burst. */
  public void schedule(PlayerRef player, List<BlockRef> plan, BlockStateRef floorData, int ticksPerBlock,
                       int durabilityCost) {
    int interval = Math.max(1, ticksPerBlock);
    int size = plan.size();
    Loc floor = plan.get(size - 1).location().add(0.5, 0.0, 0.5);
    for (int i = 1; i < size; i++) {
      BlockRef block = plan.get(i);
      float pitch = (float) (1.1D + (0.4D * ((double) i / size)));
      int delay = i * interval;
      Tasks.runEntity(player, () -> dig(player, block, pitch, durabilityCost), delay);
    }

    Tasks.runAt(floor, () -> bottomOut(floor, floorData), (size * interval) + interval);
  }

  private void bottomOut(Loc floor, BlockStateRef floorData) {
    ExcavationBurrow.bottomOutFx(owner, floor, floorData);
  }

  /** Digs one block of the shaft; false when it is no longer diggable or the shovel cannot pay. */
  public boolean dig(PlayerRef player, BlockRef block, float pitch, int durabilityCost) {
    Loc location = block.location();
    if (!player.isOnline()
        || !player.world().uid().equals(block.world().uid())
        || (Tasks.isFoliaThreading()
        && (!Tasks.isOwnedByCurrentRegion(player) || !Tasks.isOwnedByCurrentRegion(location)))) {
      return false;
    }

    if (!ExcavationBlocks.isShovelable(block.type())
        || block.relative(BlockFace.DOWN).type().equals(LAVA)
        || !owner.canBlockBreak(player, location)) {
      return false;
    }

    ItemRef hand = player.inventory().mainHand();
    if (!owner.isShovel(hand)
        || !canApplyDurability(hand, durabilityCost)
        || !ProtectionProbes.attemptBlockBreakProbe(player, block)) {
      return false;
    }

    BlockStateRef data = block.state();
    if (!block.breakNaturally(hand)) {
      return false;
    }

    applyDurability(player, hand, durabilityCost);
    onDug.accept(player, block);
    ExcavationBurrow.digFx(owner, block.center(), data, pitch);
    return true;
  }

  public static boolean canApplyDurability(ItemRef hand, int cost) {
    if (cost <= 0) {
      return true;
    }

    if (hand == null || hand.isEmpty()) {
      return false;
    }

    return hand.damage() + cost < maxDurability(hand);
  }

  private static void applyDurability(PlayerRef p, ItemRef hand, int cost) {
    if (cost <= 0 || hand == null || hand.isEmpty()) {
      return;
    }

    int currentDamage = hand.damage();
    if (currentDamage + cost >= maxDurability(hand)) {
      return;
    }

    hand.setDamage(currentDamage + cost);
    p.inventory().setMainHand(hand);
  }

  public static int maxDurability(ItemRef hand) {
    return AdaptCore.platform().host().items().maxDurability(hand.type());
  }
}
