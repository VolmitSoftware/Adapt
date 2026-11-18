package art.arcane.adapt.bukkit.platform.world;

import art.arcane.adapt.platform.value.Id;
import org.bukkit.TreeType;

/** Vanilla tree feature ids to the Bukkit tree types that place them. */
public final class BukkitTrees {
  private BukkitTrees() {
  }

  public static TreeType treeType(Id feature) {
    if (feature == null || !feature.isMinecraft()) {
      return null;
    }
    return switch (feature.path()) {
      case "oak" -> TreeType.TREE;
      case "fancy_oak" -> TreeType.BIG_TREE;
      case "spruce" -> TreeType.REDWOOD;
      case "pine" -> TreeType.TALL_REDWOOD;
      case "birch" -> TreeType.BIRCH;
      case "jungle_tree_no_vine" -> TreeType.SMALL_JUNGLE;
      case "mega_jungle_tree" -> TreeType.JUNGLE;
      case "acacia" -> TreeType.ACACIA;
      case "dark_oak" -> TreeType.DARK_OAK;
      case "cherry" -> TreeType.CHERRY;
      case "mangrove" -> TreeType.MANGROVE;
      case "tall_mangrove" -> TreeType.TALL_MANGROVE;
      case "azalea_tree" -> TreeType.AZALEA;
      default -> null;
    };
  }
}
