package art.arcane.adapt.platform.event.inventory;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerEvent;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.Objects;

/**
 * A player opens an inventory view. holderBlock() is the container block (left half for double
 * chests); holderEntity() is the villager or entity that owns the inventory. Mutable: cancelled.
 */
public class InventoryOpenEvent extends PlayerEvent {
  private final InventoryKind kind;
  private final InventoryRef inventory;
  private final BlockRef holderBlock;
  private final EntityRef holderEntity;

  public InventoryOpenEvent(PlayerRef player, InventoryKind kind, InventoryRef inventory, BlockRef holderBlock, EntityRef holderEntity) {
    super(player);
    this.kind = Objects.requireNonNull(kind, "kind");
    this.inventory = Objects.requireNonNull(inventory, "inventory");
    this.holderBlock = holderBlock;
    this.holderEntity = holderEntity;
  }

  public InventoryKind kind() {
    return kind;
  }

  public InventoryRef inventory() {
    return inventory;
  }

  public BlockRef holderBlock() {
    return holderBlock;
  }

  public EntityRef holderEntity() {
    return holderEntity;
  }
}
