package art.arcane.adapt.bukkit.platform.data;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.PistonMoveReaction;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Keeps {@link BlockDataStore} entries attached to their blocks: removed with the block, moved by pistons. */
public final class BlockDataListener implements Listener {
  private final Plugin plugin;

  public BlockDataListener(Plugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBreakEvent event) {
    BlockDataStore.clear(plugin, event.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPlaceEvent event) {
    if (!BlockDataStore.writtenThisTick(event.getBlock())) {
      BlockDataStore.clear(plugin, event.getBlock());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityChangeBlockEvent event) {
    if (event.getTo() != event.getBlock().getType()) {
      BlockDataStore.clear(plugin, event.getBlock());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockExplodeEvent event) {
    clearAll(event.blockList());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityExplodeEvent event) {
    clearAll(event.blockList());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBurnEvent event) {
    BlockDataStore.clear(plugin, event.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockFadeEvent event) {
    Block block = event.getBlock();
    if (block.getType() != Material.FIRE && event.getNewState().getType() != block.getType()) {
      BlockDataStore.clear(plugin, block);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(StructureGrowEvent event) {
    clearStates(event.getBlocks());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockFertilizeEvent event) {
    clearStates(event.getBlocks());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPistonExtendEvent event) {
    move(event.getBlocks(), event.getDirection());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPistonRetractEvent event) {
    move(event.getBlocks(), event.getDirection());
  }

  private void move(List<Block> blocks, BlockFace direction) {
    List<Move> moves = new ArrayList<>();
    for (Block block : blocks) {
      PersistentDataContainer data = BlockDataStore.read(plugin, block);
      if (data == null || data.isEmpty()) {
        continue;
      }
      if (block.getPistonMoveReaction() == PistonMoveReaction.BREAK) {
        BlockDataStore.clear(plugin, block);
      } else {
        moves.add(new Move(block, block.getRelative(direction), data));
      }
    }
    for (int index = moves.size() - 1; index >= 0; index--) {
      Move move = moves.get(index);
      BlockDataStore.edit(plugin, move.to(), target -> move.data().copyTo(target, true));
      BlockDataStore.clear(plugin, move.from());
    }
  }

  private void clearAll(Collection<Block> blocks) {
    for (Block block : blocks) {
      BlockDataStore.clear(plugin, block);
    }
  }

  private void clearStates(Collection<BlockState> states) {
    for (BlockState state : states) {
      BlockDataStore.clear(plugin, state.getBlock());
    }
  }

  private record Move(Block from, Block to, PersistentDataContainer data) {
  }
}
