package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.Set;

/** The blocks that burn a player who stands in or on them, and whether a player set one out. */
public final class NetherFireSources {
  private static final Set<Id> SOURCES = Set.of(Id.minecraft("fire"), Id.minecraft("soul_fire"), Id.minecraft("lava"),
      Id.minecraft("magma_block"), Id.minecraft("campfire"), Id.minecraft("soul_campfire"));
  private static final Set<Id> FLAMES = Set.of(Id.minecraft("fire"), Id.minecraft("soul_fire"));
  private static final BlockFace[] FUEL_FACES = {BlockFace.DOWN, BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
      BlockFace.WEST};

  private NetherFireSources() {
  }

  public static boolean isSource(Id type) {
    return SOURCES.contains(type);
  }

  /**
   * Whether a fire source within {@code radius} blocks of {@code center} is farmed: a placed source, or a flame
   * burning on or against a placed block, which covers fire that spread from a placed flame along placed fuel.
   */
  public static boolean placedNear(BlockRef center, int radius) {
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dy = -radius; dy <= radius; dy++) {
        for (int dz = -radius; dz <= radius; dz++) {
          BlockRef block = center.relative(dx, dy, dz);
          Id type = block.type();
          if (!SOURCES.contains(type)) {
            continue;
          }
          if (Mutations.farmed(block) || (FLAMES.contains(type) && feedsOnPlaced(block))) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean feedsOnPlaced(BlockRef flame) {
    for (BlockFace face : FUEL_FACES) {
      if (Mutations.farmed(flame.relative(face))) {
        return true;
      }
    }
    return false;
  }
}
