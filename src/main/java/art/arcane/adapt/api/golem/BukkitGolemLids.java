package art.arcane.adapt.api.golem;

import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Lidded;

final class BukkitGolemLids implements GolemLids {
  static final BukkitGolemLids INSTANCE = new BukkitGolemLids();

  private BukkitGolemLids() {
  }

  @Override
  public void open(Block container) {
    run(container, true);
  }

  @Override
  public void close(Block container) {
    run(container, false);
  }

  static void apply(Block container, boolean open) {
    if (!(PaperCompat.liveState(container) instanceof Lidded lid)) {
      return;
    }
    if (open) {
      lid.open();
    } else {
      lid.close();
    }
  }

  private static void run(Block container, boolean open) {
    Location at = container.getLocation();
    if (J.isOwnedByCurrentRegion(at)) {
      apply(container, open);
      return;
    }
    J.runAt(at, () -> apply(container, open));
  }
}
