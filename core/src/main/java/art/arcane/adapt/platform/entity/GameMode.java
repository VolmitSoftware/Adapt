package art.arcane.adapt.platform.entity;

public enum GameMode {
  SURVIVAL,
  CREATIVE,
  ADVENTURE,
  SPECTATOR;

  public boolean isSurvivalLike() {
    return this == SURVIVAL || this == ADVENTURE;
  }
}
