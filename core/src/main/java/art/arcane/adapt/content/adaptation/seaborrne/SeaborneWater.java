package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.Set;

/** Water and bubble columns, the blocks a diver can be in. */
public final class SeaborneWater {
  private static final Set<Id> WATER = Set.of(Id.minecraft("water"), Id.minecraft("bubble_column"));

  private SeaborneWater() {
  }

  public static boolean isWater(BlockRef block) {
    return WATER.contains(block.type());
  }

  /** The player's eyes are in water. */
  public static boolean underwater(PlayerRef player) {
    return isWater(player.world().blockAt(player.eyeLocation()));
  }
}
