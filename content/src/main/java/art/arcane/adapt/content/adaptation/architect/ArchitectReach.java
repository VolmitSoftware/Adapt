package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockHit;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.util.List;

/** Placing the held block where the player aims beyond their normal reach, or onto a chalk guide near their aim. */
public final class ArchitectReach {
  /** A survival player's block reach where the server has no reach attribute. */
  public static final double VANILLA_REACH = 4.5D;
  private static final double SNAP_DISTANCE = 1.5D;
  private static final double STEP_BACK = 0.05D;

  private ArchitectReach() {
  }

  /** The open spot in front of the first block the look ray meets within {@code reach}, or null. */
  public static BlockRef aimed(PlayerRef player, double reach) {
    Loc eye = player.eyeLocation();
    Vec3 look = eye.direction().normalize();
    BlockHit hit = player.world().rayTraceBlockHit(eye.position(), look, reach, false);
    if (hit == null) {
      return null;
    }
    BlockRef spot = player.world().blockAt(hit.position().subtract(look.multiply(STEP_BACK)).toBlockPos());
    return open(spot) ? spot : null;
  }

  /** The open guide spot within {@code reach} closest to the look ray, at most 1.5 blocks off it, or null. */
  public static BlockRef snapped(PlayerRef player, List<BlockRef> guide, double reach) {
    Loc eye = player.eyeLocation();
    Vec3 look = eye.direction().normalize();
    BlockRef best = null;
    double bestOffset = SNAP_DISTANCE * SNAP_DISTANCE;
    for (BlockRef spot : guide) {
      Vec3 toward = spot.center().position().subtract(eye.position());
      double along = toward.dot(look);
      double offset = toward.lengthSquared() - (along * along);
      if (along > 0D && toward.lengthSquared() <= reach * reach && offset <= bestOffset) {
        best = spot;
        bestOffset = offset;
      }
    }
    return best;
  }

  /**
   * Places one of the held blocks at the spot as the player, where they may build and protection allows it, and records
   * it as player-placed; false when nothing was placed.
   */
  public static boolean placeHeld(Adaptation<?> owner, PlayerRef player, BlockRef spot) {
    ItemRef hand = player.inventory().mainHand();
    ItemOps items = AdaptApi.platform().host().items();
    if (hand == null || hand.isEmpty() || !items.isBlock(hand.type()) || !open(spot)) {
      return false;
    }
    BlockStateRef state = items.blockState(hand.type());
    if (state == null || !owner.canBlockPlace(player, spot.location())
        || !ProtectionProbes.attemptBlockPlaceProbe(player, spot)) {
      return false;
    }
    spot.setState(state, true);
    XpProvenance.recordPlacement(spot);
    if (player.gameMode() != GameMode.CREATIVE) {
      hand.setAmount(hand.amount() - 1);
      player.inventory().setMainHand(hand);
    }
    return true;
  }

  private static boolean open(BlockRef spot) {
    return ArchitectChalkLine.isGuideSpaceDrawable(spot);
  }
}
