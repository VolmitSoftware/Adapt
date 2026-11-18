package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.Objects;

/** Game mode is about to change. Mutable: cancelled. */
public class PlayerGameModeChangeEvent extends PlayerEvent {
  private final GameMode newGameMode;

  public PlayerGameModeChangeEvent(PlayerRef player, GameMode newGameMode) {
    super(player);
    this.newGameMode = Objects.requireNonNull(newGameMode, "newGameMode");
  }

  public GameMode newGameMode() {
    return newGameMode;
  }
}
