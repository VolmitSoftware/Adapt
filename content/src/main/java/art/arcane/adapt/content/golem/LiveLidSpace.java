package art.arcane.adapt.content.golem;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.LidRef;

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
      if (AdaptApi.scheduler().owns(half.location())) {
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
    return AdaptApi.scheduler().runAt(half.location(), task, Math.max(1, delayTicks));
  }

  private static boolean ready(BlockRef block) {
    return block.world().isChunkLoaded(block.x() >> 4, block.z() >> 4) && AdaptApi.scheduler().owns(block.location());
  }
}
