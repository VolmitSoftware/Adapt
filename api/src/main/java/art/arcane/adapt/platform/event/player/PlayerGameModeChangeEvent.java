package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** A player's game mode is about to change. Cancelling keeps the current mode. Mutable: cancelled. */
@ApiStatus.AvailableSince("1.0.0")
public class PlayerGameModeChangeEvent extends PlayerEvent {
  private final GameMode newGameMode;

  public PlayerGameModeChangeEvent(PlayerRef player, GameMode newGameMode) {
    super(player);
    this.newGameMode = Objects.requireNonNull(newGameMode, "newGameMode");
  }

  /** The mode the player is switching to. */
  public GameMode newGameMode() {
    return newGameMode;
  }
}
