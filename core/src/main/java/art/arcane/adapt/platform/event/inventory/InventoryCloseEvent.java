package art.arcane.adapt.platform.event.inventory;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;

import java.util.Objects;

/** A player closed an inventory view. Adapt menus report closes through their own callback. */
public class InventoryCloseEvent extends AdaptEvent {
  private final PlayerRef player;
  private final InventoryKind viewKind;

  public InventoryCloseEvent(PlayerRef player, InventoryKind viewKind) {
    this.player = Objects.requireNonNull(player, "player");
    this.viewKind = Objects.requireNonNull(viewKind, "viewKind");
  }

  public PlayerRef player() {
    return player;
  }

  public InventoryKind viewKind() {
    return viewKind;
  }
}
