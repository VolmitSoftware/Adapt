package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** A player clicks a recipe in the recipe book. Read-only. */
@ApiStatus.AvailableSince("1.0.0")
public class PlayerRecipeBookClickEvent extends AdaptEvent {
  private final PlayerRef player;
  private final Id recipe;
  private final boolean makeAll;

  public PlayerRecipeBookClickEvent(PlayerRef player, Id recipe, boolean makeAll) {
    this.player = Objects.requireNonNull(player, "player");
    this.recipe = recipe;
    this.makeAll = makeAll;
  }

  /** The player who clicked. */
  public PlayerRef player() {
    return player;
  }

  /** The clicked recipe's key, or null for a recipe without one. */
  public Id recipe() {
    return recipe;
  }

  /** True when the player shift-clicked to fill as many crafts as the ingredients allow. */
  public boolean makeAll() {
    return makeAll;
  }
}
