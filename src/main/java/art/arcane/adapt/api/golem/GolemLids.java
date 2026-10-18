package art.arcane.adapt.api.golem;

import org.bukkit.block.Block;

interface GolemLids {
  void open(Block container);

  void close(Block container);
}
