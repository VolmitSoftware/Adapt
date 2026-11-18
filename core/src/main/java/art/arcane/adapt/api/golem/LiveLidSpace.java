package art.arcane.adapt.api.golem;

import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.LidRef;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class LiveLidSpace implements LidSpace {
  static final LiveLidSpace INSTANCE = new LiveLidSpace();

  private LiveLidSpace() {
  }

  @Override
  public List<BlockRef> halves(BlockRef container) {
    if (!ready(container) || container.lid().isEmpty()) {
      return List.of();
    }
    List<BlockRef> halves = container.containerHalves();
    if (halves.size() < 2) {
      return halves;
    }
    List<BlockRef> owned = new ArrayList<>(halves.size());
    for (BlockRef half : halves) {
      if (Tasks.isOwnedByCurrentRegion(half.location())) {
        owned.add(half);
      }
    }
    return owned;
  }

  @Override
  public Optional<LidRef> lid(BlockRef half) {
    return ready(half) ? half.lid() : Optional.empty();
  }

  @Override
  public boolean runAt(BlockRef half, Runnable task, int delayTicks) {
    return Tasks.runAt(half.location(), task, Math.max(1, delayTicks));
  }

  private static boolean ready(BlockRef block) {
    return block.world().isChunkLoaded(block.x() >> 4, block.z() >> 4) && Tasks.isOwnedByCurrentRegion(block.location());
  }
}
