package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.Cancellable;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.Objects;

/**
 * Click on air or a block, or physical trigger. Cancellation follows Bukkit: the event counts as
 * cancelled when the block use is denied, and a platform posts air clicks with the block use denied, so
 * an untouched air click reports cancelled as on Bukkit. {@link #isUseDenied()} is the relevant use
 * (the block use when a block was clicked, else the item use). {@code setCancelled(true)} denies both;
 * {@code setCancelled(false)} resets denied uses to DEFAULT. Mutable: block use, item use.
 */
public class PlayerInteractEvent extends AdaptEvent implements Cancellable {
  private final PlayerRef player;
  private final InteractAction action;
  private final Hand hand;
  private final ItemRef item;
  private final BlockRef clickedBlock;
  private final BlockFace clickedFace;
  private final Vec3 clickedPosition;
  private UseResult useBlock;
  private UseResult useItem;

  public PlayerInteractEvent(PlayerRef player, InteractAction action, Hand hand, ItemRef item, BlockRef clickedBlock,
                             BlockFace clickedFace, Vec3 clickedPosition, UseResult useBlock, UseResult useItem) {
    this.player = Objects.requireNonNull(player, "player");
    this.action = Objects.requireNonNull(action, "action");
    this.hand = hand;
    this.item = item;
    this.clickedBlock = clickedBlock;
    this.clickedFace = clickedFace;
    this.clickedPosition = clickedPosition;
    this.useBlock = useBlock == null ? UseResult.DEFAULT : useBlock;
    this.useItem = useItem == null ? UseResult.DEFAULT : useItem;
  }

  public PlayerRef player() {
    return player;
  }

  public InteractAction action() {
    return action;
  }

  /** Hand used, or null for physical actions. */
  public Hand hand() {
    return hand;
  }

  /** Item in that hand; empty or null when none. */
  public ItemRef item() {
    return item;
  }

  public BlockRef clickedBlock() {
    return clickedBlock;
  }

  public BlockFace clickedFace() {
    return clickedFace;
  }

  /** Exact hit position on the block, or null. */
  public Vec3 clickedPosition() {
    return clickedPosition;
  }

  public boolean hasBlock() {
    return clickedBlock != null;
  }

  public boolean hasItem() {
    return item != null && !item.isEmpty();
  }

  public UseResult useBlock() {
    return useBlock;
  }

  public void setUseBlock(UseResult result) {
    this.useBlock = Objects.requireNonNull(result, "result");
  }

  public UseResult useItem() {
    return useItem;
  }

  public void setUseItem(UseResult result) {
    this.useItem = Objects.requireNonNull(result, "result");
  }

  @Override
  public boolean isCancelled() {
    return useBlock == UseResult.DENY;
  }

  /** The use this click is about is denied: the block use when a block was clicked, else the item use. */
  public boolean isUseDenied() {
    return clickedBlock != null ? useBlock == UseResult.DENY : useItem == UseResult.DENY;
  }

  @Override
  public void setCancelled(boolean cancelled) {
    if (cancelled) {
      useBlock = UseResult.DENY;
      useItem = UseResult.DENY;
      return;
    }
    if (useBlock == UseResult.DENY) {
      useBlock = UseResult.DEFAULT;
    }
    if (useItem == UseResult.DENY) {
      useItem = UseResult.DEFAULT;
    }
  }
}
