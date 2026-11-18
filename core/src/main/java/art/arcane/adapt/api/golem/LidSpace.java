package art.arcane.adapt.api.golem;

import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.LidRef;

import java.util.List;
import java.util.Optional;

interface LidSpace {
  List<BlockRef> halves(BlockRef container);

  Optional<LidRef> lid(BlockRef half);

  boolean runAt(BlockRef half, Runnable task, int delayTicks);
}
