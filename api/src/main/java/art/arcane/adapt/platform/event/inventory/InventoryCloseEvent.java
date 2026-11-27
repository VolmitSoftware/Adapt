package art.arcane.adapt.platform.event.inventory;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** A player closed an inventory view. Adapt menus report closes through their own callback. */
@ApiStatus.AvailableSince("1.0.0")
public class InventoryCloseEvent extends AdaptEvent {
  private final PlayerRef player;
  private final InventoryKind viewKind;

  public InventoryCloseEvent(PlayerRef player, InventoryKind viewKind) {
    this.player = Objects.requireNonNull(player, "player");
    this.viewKind = Objects.requireNonNull(viewKind, "viewKind");
  }

  /** The player who closed the view. */
  public PlayerRef player() {
    return player;
  }

  /** Kind of the top inventory of the closed view. */
  public InventoryKind viewKind() {
    return viewKind;
  }
}
