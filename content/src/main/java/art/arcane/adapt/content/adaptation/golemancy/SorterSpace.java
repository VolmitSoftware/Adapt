package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.content.golem.node.NodeIndex;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;
import java.util.UUID;

interface SorterSpace {
  List<BlockRef> containers(WorldRef world, int x, int y, int z, int radius);

  List<NodeIndex.Entry> nodes(UUID world, double x, double y, double z, double range);
}
