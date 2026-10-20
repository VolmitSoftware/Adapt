package art.arcane.adapt.api.golem;

import org.bukkit.block.Block;

import java.util.List;
import java.util.Optional;

interface LidSpace {
  List<Block> halves(Block container);

  Optional<Lid> lid(Block half);

  boolean runAt(Block half, Runnable task, int delayTicks);

  interface Lid {
    boolean viewed();

    boolean isOpen();

    void open();

    void close();
  }
}
