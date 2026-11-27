package art.arcane.adapt.content.golem.node;

import art.arcane.adapt.api.content.ContentIds;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;

public final class NodeIcons {
  public static final float ICON_SCALE = 0.5F;
  private static final float SPRITE_DEPTH = 1F / 16F;
  private static final float CHEST_MODEL_DEPTH = 14F / 16F;
  private static final float BLOCK_FIXED_SCALE = 0.5F;
  private static final String COPPER_CHEST = "copper_chest";
  private static final Id CHEST = Id.minecraft("chest");
  private static final Id TRAPPED_CHEST = Id.minecraft("trapped_chest");

  private NodeIcons() {
  }

  public static Id icon(NodeType type) {
    return switch (type) {
      case GUARD -> Id.minecraft("iron_sword");
      case COLLECTION -> ContentIds.itemOr(Id.minecraft("bundle"), Id.minecraft("hopper"));
      case DEPOSIT -> CHEST;
      case HARVEST -> Id.minecraft("iron_hoe");
      case LUMBER -> Id.minecraft("iron_axe");
      case BREAKER -> Id.minecraft("iron_pickaxe");
      case PASTURE -> Id.minecraft("lead");
      case INPUT -> ContentIds.itemOr(Id.minecraft("copper_chest"), Id.minecraft("barrel"));
      case FILTER -> Id.minecraft("item_frame");
      case FISHING -> Id.minecraft("fishing_rod");
    };
  }

  public static float halfDepth(Id icon) {
    return renderedDepth(icon) * ICON_SCALE * 0.5F;
  }

  public static Vec3 faceOffset(Id icon) {
    return new Vec3(0D, 0D, halfDepth(icon));
  }

  private static float renderedDepth(Id icon) {
    if (icon.equals(CHEST) || icon.equals(TRAPPED_CHEST) || icon.path().endsWith(COPPER_CHEST)) {
      return CHEST_MODEL_DEPTH * BLOCK_FIXED_SCALE;
    }
    return SPRITE_DEPTH;
  }
}
