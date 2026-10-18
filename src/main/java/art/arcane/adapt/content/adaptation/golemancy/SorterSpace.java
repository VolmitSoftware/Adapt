package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.node.NodeIndex;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;
import java.util.UUID;

interface SorterSpace {
  List<Block> containers(World world, int x, int y, int z, int radius);

  List<NodeIndex.Entry> nodes(UUID world, double x, double y, double z, double range);
}
