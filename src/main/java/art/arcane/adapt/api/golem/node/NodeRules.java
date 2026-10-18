package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.golem.NodeType;
import org.bukkit.Material;
import org.bukkit.block.Block;

public final class NodeRules {
  public static final int PRIORITY_LEVEL = 3;
  public static final int REDSTONE_LEVEL = 4;
  public static final int BLACKLIST_LEVEL = 5;
  private static final String SHULKER_BOX = "SHULKER_BOX";
  private static final String COPPER_CHEST = "COPPER_CHEST";

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

  public static NodeValidation validate(NodeType type, Material anchor) {
    return anchorValid(type, anchor) ? NodeValidation.KEEP : NodeValidation.DROP;
  }

  public static boolean anchorValid(NodeType type, Material anchor) {
    return needsContainer(type) ? isContainer(anchor) : !isEmpty(anchor);
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
    String name = material.name();
    return name.endsWith(SHULKER_BOX) || name.endsWith(COPPER_CHEST);
  }

  public static boolean active(NodeRecord record, Block anchor) {
    if (record.redstone() == RedstoneMode.IGNORE) {
      return true;
    }
    return active(record.redstone(), anchor.isBlockPowered() || anchor.isBlockIndirectlyPowered());
  }

  public static boolean active(RedstoneMode mode, boolean powered) {
    return switch (mode) {
      case IGNORE -> true;
      case POWERED -> powered;
      case UNPOWERED -> !powered;
    };
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

  private static boolean isEmpty(Material material) {
    return switch (material) {
      case AIR, CAVE_AIR, VOID_AIR, WATER, LAVA, BUBBLE_COLUMN, FIRE, SOUL_FIRE, LIGHT, STRUCTURE_VOID -> true;
      default -> false;
    };
  }
}
