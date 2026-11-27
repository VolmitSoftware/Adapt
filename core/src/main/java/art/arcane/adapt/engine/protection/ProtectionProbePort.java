package art.arcane.adapt.engine.protection;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockChange;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.protection.ProtectionCheck;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.List;

/**
 * Synthetic platform events that let any protection plugin veto an action without it happening: a fake break
 * before Adapt removes a block itself, fake pickup events before Adapt moves a drop into an inventory, or an
 * ordinary break event every listener sees ({@link #callBreak}). Bukkit
 * fires the real event classes; the neutral fallback asks the platform's protection host.
 */
public interface ProtectionProbePort {
  ProtectionProbePort NONE = new ProtectionProbePort() {
  };

  /** True when nothing vetoes {@code player} breaking {@code block}. The block is left in place. */
  default boolean probeBreak(PlayerRef player, BlockRef block) {
    return AdaptCore.platform().protection().allows(player, ProtectionCheck.BLOCK_BREAK, block.location(), null);
  }

  /**
   * Fires an ordinary (not probe) break event for {@code player} breaking {@code block}, seen by every listener
   * including Adapt's own skills, and returns true when nobody cancelled it. The block is left in place; the caller
   * breaks it. The neutral version asks the protection host and posts the event on Adapt's bus.
   */
  default boolean callBreak(PlayerRef player, BlockRef block) {
    BlockBreakEvent event = new BlockBreakEvent(player, block, true, 0);
    event.setCancelled(!AdaptCore.platform().protection().allows(player, ProtectionCheck.BLOCK_BREAK, block.location(), null));
    AdaptCore.get().getBus().post(event);
    return !event.isCancelled();
  }

  /** True when nothing vetoes {@code player} using (right-clicking) {@code block}. Nothing is used. */
  default boolean probeUse(PlayerRef player, BlockRef block) {
    return AdaptCore.platform().protection().allows(player, ProtectionCheck.INTERACT, block.location(), null);
  }

  /** True when nothing vetoes {@code player} placing at {@code block} with the main hand item. Nothing is placed. */
  default boolean probePlace(PlayerRef player, BlockRef block) {
    return AdaptCore.platform().protection().allows(player, ProtectionCheck.BLOCK_PLACE, block.location(), null);
  }

  /**
   * Fires an ordinary place event for {@code block}, which the caller has already set, placed by {@code player} with
   * {@code item} over {@code replaced}: protection and block-logging plugins see a normal placement, while Adapt's own
   * skills and adaptations skip it. True when nobody cancelled it; on false the caller restores {@code replaced}.
   */
  default boolean callPlace(PlayerRef player, BlockRef block, BlockStateRef replaced, BlockRef against, ItemRef item) {
    return probePlace(player, block);
  }

  /**
   * Fires an ordinary break event for {@code player} removing {@code block}, which protection and block-logging plugins
   * see while Adapt's own skills and adaptations skip it. True when nobody cancelled it. The block stays; the caller
   * removes it without drops.
   */
  default boolean callRemove(PlayerRef player, BlockRef block) {
    return probeBreak(player, block);
  }

  /**
   * True when nothing vetoes {@code player} growing the tree {@code feature} at {@code origin} into exactly
   * {@code blocks}. Nothing is placed.
   */
  default boolean probeTreeGrow(PlayerRef player, BlockRef origin, Id feature, List<BlockChange> blocks) {
    return true;
  }

  /**
   * True when nothing vetoes {@code player} picking up {@code item} from a block drop, leaving
   * {@code remaining} items on the ground. The item entity is not in the world yet.
   */
  default boolean probeDropPickup(PlayerRef player, ItemEntityRef item, int remaining, Loc source) {
    return true;
  }

  /** True when nothing vetoes {@code player} picking up the live {@code item}, leaving {@code remaining} items. */
  default boolean probeItemPickup(PlayerRef player, ItemEntityRef item, int remaining) {
    return true;
  }

  /** True when nothing vetoes the non-player {@code entity} picking up the live {@code item}, leaving {@code remaining}. */
  default boolean probeEntityPickup(LivingRef entity, ItemEntityRef item, int remaining) {
    return true;
  }
}
