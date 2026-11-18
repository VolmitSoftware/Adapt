package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Items about to drop from a block a player broke. Mutable: the item list (remove an entry to stop
 * it from spawning, add one from {@code WorldRef.createItemEntity} to spawn it), cancelled (no drops at all).
 */
public class BlockDropItemEvent extends PlayerEvent {
  private final BlockRef block;
  private final BlockStateRef brokenState;
  private final List<ItemEntityRef> items;
  private final Id brokenSpawnerType;
  private final DataHolder brokenBlockEntityData;

  public BlockDropItemEvent(PlayerRef player, BlockRef block, BlockStateRef brokenState, List<ItemEntityRef> items) {
    this(player, block, brokenState, items, null);
  }

  public BlockDropItemEvent(PlayerRef player, BlockRef block, BlockStateRef brokenState, List<ItemEntityRef> items,
                            Id brokenSpawnerType) {
    this(player, block, brokenState, items, brokenSpawnerType, null);
  }

  public BlockDropItemEvent(PlayerRef player, BlockRef block, BlockStateRef brokenState, List<ItemEntityRef> items,
                            Id brokenSpawnerType, DataHolder brokenBlockEntityData) {
    super(player);
    this.block = Objects.requireNonNull(block, "block");
    this.brokenState = Objects.requireNonNull(brokenState, "brokenState");
    this.items = new ArrayList<>(items);
    this.brokenSpawnerType = brokenSpawnerType;
    this.brokenBlockEntityData = brokenBlockEntityData;
  }

  public BlockRef block() {
    return block;
  }

  public BlockStateRef brokenState() {
    return brokenState;
  }

  public List<ItemEntityRef> items() {
    return items;
  }

  /** Entity type the broken block spawned when it was a mob spawner, else null. */
  public Id brokenSpawnerType() {
    return brokenSpawnerType;
  }

  /** Snapshot of the broken block entity's data ({@code BlockRef#blockEntityData}), null when it had none. */
  public DataHolder brokenBlockEntityData() {
    return brokenBlockEntityData;
  }
}
