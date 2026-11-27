package art.arcane.adapt.api.xp;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * XP for moving items that pays about one stack of each material per window and player, so moving the same items
 * back and forth does not farm XP.
 */
@ApiStatus.Experimental
public final class ItemTransferXp {
  private ItemTransferXp() {
  }

  /**
   * How many of {@code amount} items of {@code material} still pay: the player's allowance refills by
   * {@code stackSize} per {@code windowMillis} and holds at most {@code stackSize}. The paid part is used up; 0 without
   * loaded player data.
   */
  public static double claim(PlayerRef player, String material, int amount, int stackSize, long windowMillis) {
    return Engine.bridge().progress().claimTransfer(Objects.requireNonNull(player, "player"),
        Objects.requireNonNull(material, "material"), amount, stackSize, windowMillis);
  }
}
