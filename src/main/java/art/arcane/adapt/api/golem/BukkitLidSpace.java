package art.arcane.adapt.api.golem;

import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.Lidded;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class BukkitLidSpace implements LidSpace {
  static final BukkitLidSpace INSTANCE = new BukkitLidSpace();

  private BukkitLidSpace() {
  }

  @Override
  public List<Block> halves(Block container) {
    if (!ready(container)) {
      return List.of();
    }
    List<Block> halves = halves(container, PaperCompat.liveState(container));
    if (halves.size() < 2) {
      return halves;
    }
    List<Block> owned = new ArrayList<>(halves.size());
    for (Block half : halves) {
      if (J.isOwnedByCurrentRegion(half.getLocation())) {
        owned.add(half);
      }
    }
    return owned;
  }

  @Override
  public Optional<Lid> lid(Block half) {
    if (!ready(half)) {
      return Optional.empty();
    }
    BlockState state = PaperCompat.liveState(half);
    return state instanceof Lidded lidded ? Optional.of(new BukkitLid(state, lidded)) : Optional.empty();
  }

  @Override
  public boolean runAt(Block half, Runnable task, int delayTicks) {
    return J.runAt(half.getLocation(), task, Math.max(1, delayTicks));
  }

  static List<Block> halves(Block container, BlockState state) {
    if (!(state instanceof Lidded)) {
      return List.of();
    }
    if (state instanceof Chest chest && chest.getInventory() instanceof DoubleChestInventory pair) {
      Block left = blockOf(pair.getLeftSide());
      Block right = blockOf(pair.getRightSide());
      if (left != null && right != null) {
        return List.of(left, right);
      }
    }
    return List.of(container);
  }

  private static Block blockOf(Inventory half) {
    Location at = half.getLocation();
    return at == null || at.getWorld() == null ? null : at.getBlock();
  }

  private static boolean ready(Block block) {
    return block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4) && J.isOwnedByCurrentRegion(block.getLocation());
  }

  static final class BukkitLid implements Lid {
    private final BlockState state;
    private final Lidded lidded;

    BukkitLid(BlockState state, Lidded lidded) {
      this.state = state;
      this.lidded = lidded;
    }

    @Override
    public boolean viewed() {
      return state instanceof Container container && !container.getInventory().getViewers().isEmpty();
    }

    @Override
    public boolean isOpen() {
      return lidded.isOpen();
    }

    @Override
    public void open() {
      lidded.open();
    }

    @Override
    public void close() {
      lidded.close();
    }
  }
}
