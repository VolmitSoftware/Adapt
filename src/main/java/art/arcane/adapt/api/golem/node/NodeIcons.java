package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.golem.NodeType;
import org.bukkit.Material;
import org.joml.Vector3f;

public final class NodeIcons {
  public static final float ICON_SCALE = 0.5F;
  private static final float SPRITE_DEPTH = 1F / 16F;
  private static final float CHEST_MODEL_DEPTH = 14F / 16F;
  private static final float BLOCK_FIXED_SCALE = 0.5F;
  private static final String COPPER_CHEST = "COPPER_CHEST";

  private NodeIcons() {
  }

  public static Material icon(NodeType type) {
    return switch (type) {
      case GUARD -> Material.IRON_SWORD;
      case COLLECTION -> Material.BUNDLE;
      case DEPOSIT -> Material.CHEST;
      case HARVEST -> Material.IRON_HOE;
      case LUMBER -> Material.IRON_AXE;
      case BREAKER -> Material.IRON_PICKAXE;
      case PASTURE -> Material.LEAD;
      case INPUT -> Material.COPPER_CHEST;
      case FILTER -> Material.ITEM_FRAME;
      case FISHING -> Material.FISHING_ROD;
    };
  }

  public static float halfDepth(Material icon) {
    return renderedDepth(icon) * ICON_SCALE * 0.5F;
  }

  public static Vector3f faceOffset(Material icon) {
    return new Vector3f(0F, 0F, halfDepth(icon));
  }

  private static float renderedDepth(Material icon) {
    if (icon == Material.CHEST || icon == Material.TRAPPED_CHEST || icon.name().endsWith(COPPER_CHEST)) {
      return CHEST_MODEL_DEPTH * BLOCK_FIXED_SCALE;
    }
    return SPRITE_DEPTH;
  }
}
