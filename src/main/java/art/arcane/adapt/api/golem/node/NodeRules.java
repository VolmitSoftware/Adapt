package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.golem.NodeType;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public final class NodeRules {
  public static final int PRIORITY_LEVEL = 3;
  public static final int REDSTONE_LEVEL = 4;
  public static final int BLACKLIST_LEVEL = 5;
  private static final String COPPER_CHEST = "COPPER_CHEST";
  private static final int POWER_MARGIN = 2;

  private NodeRules() {
  }

  public static NodePlacementVerdict placement(NodePlacementCheck check) {
    if (!check.rosterReady()) {
      return NodePlacementVerdict.UNAVAILABLE;
    }
    if (!check.chunkReady()) {
      return NodePlacementVerdict.NOT_LOADED;
    }
    if (!check.faceFree()) {
      return NodePlacementVerdict.OCCUPIED;
    }
    if (!check.canBuild()) {
      return NodePlacementVerdict.PROTECTED;
    }
    if (!check.faceAllowed()) {
      return NodePlacementVerdict.NEEDS_SIDE_FACE;
    }
    if (check.needsContainer() && !check.containerAnchor()) {
      return NodePlacementVerdict.NEEDS_CONTAINER;
    }
    if (check.needsContainer() && !check.canAccessContainer()) {
      return NodePlacementVerdict.CONTAINER_PROTECTED;
    }
    if (check.chunkNodes() >= check.maxNodesPerChunk()) {
      return NodePlacementVerdict.CHUNK_FULL;
    }
    if (check.ownedNodes() >= check.nodeCap()) {
      return NodePlacementVerdict.NODE_CAP;
    }
    return NodePlacementVerdict.READY;
  }

  public static boolean lacksContainer(NodeType type, Material anchor) {
    return needsContainer(type) && !isContainer(anchor);
  }

  public static boolean faceAllowed(NodeType type, BlockFace face) {
    return !needsSideFace(type) || (face != BlockFace.UP && face != BlockFace.DOWN);
  }

  public static boolean needsSideFace(NodeType type) {
    return switch (type) {
      case FISHING -> true;
      case GUARD, COLLECTION, DEPOSIT, HARVEST, LUMBER, BREAKER, PASTURE, INPUT, FILTER -> false;
    };
  }

  public static boolean needsContainer(NodeType type) {
    return switch (type) {
      case DEPOSIT, INPUT, FILTER -> true;
      case GUARD, COLLECTION, HARVEST, LUMBER, BREAKER, PASTURE, FISHING -> false;
    };
  }

  public static boolean isContainer(Material material) {
    if (material == Material.CHEST || material == Material.TRAPPED_CHEST || material == Material.BARREL) {
      return true;
    }
    return NodeContainers.isShulkerBox(material) || material.name().endsWith(COPPER_CHEST);
  }

  public static boolean active(NodeRecord record, Block anchor) {
    return record.redstone() == RedstoneMode.IGNORE || active(record.redstone(), power(anchor));
  }

  public static boolean active(RedstoneMode mode, NodePower power) {
    return switch (mode) {
      case IGNORE -> true;
      case POWERED -> power == NodePower.ON;
      case UNPOWERED -> power == NodePower.OFF;
    };
  }

  public static NodePower power(Block anchor) {
    if (!powerReadable(anchor.getWorld(), anchor.getX(), anchor.getZ())) {
      return NodePower.UNKNOWN;
    }
    return anchor.isBlockPowered() || anchor.isBlockIndirectlyPowered() ? NodePower.ON : NodePower.OFF;
  }

  public static boolean allowsPriority(int nodeWeavingLevel) {
    return nodeWeavingLevel >= PRIORITY_LEVEL;
  }

  public static boolean allowsRedstone(int nodeWeavingLevel) {
    return nodeWeavingLevel >= REDSTONE_LEVEL;
  }

  public static boolean allowsBlacklistAndTags(int nodeWeavingLevel) {
    return nodeWeavingLevel >= BLACKLIST_LEVEL;
  }

  private static boolean powerReadable(World world, int x, int z) {
    for (int chunkX = (x - POWER_MARGIN) >> 4; chunkX <= (x + POWER_MARGIN) >> 4; chunkX++) {
      for (int chunkZ = (z - POWER_MARGIN) >> 4; chunkZ <= (z + POWER_MARGIN) >> 4; chunkZ++) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
          return false;
        }
      }
    }
    return true;
  }
}
