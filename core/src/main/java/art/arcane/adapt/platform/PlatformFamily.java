package art.arcane.adapt.platform;

public enum PlatformFamily {
  BUKKIT,
  FABRIC,
  FORGE,
  NEOFORGE;

  public boolean isModded() {
    return this != BUKKIT;
  }
}
