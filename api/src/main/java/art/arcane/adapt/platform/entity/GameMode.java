package art.arcane.adapt.platform.entity;

import org.jetbrains.annotations.ApiStatus;

/** Player game mode. */
@ApiStatus.AvailableSince("1.0.0")
public enum GameMode {
  SURVIVAL,
  CREATIVE,
  ADVENTURE,
  SPECTATOR;

  /** True for survival and adventure, the modes where players take damage and use up items. */
  public boolean isSurvivalLike() {
    return this == SURVIVAL || this == ADVENTURE;
  }
}
