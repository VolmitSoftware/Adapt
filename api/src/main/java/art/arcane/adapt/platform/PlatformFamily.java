package art.arcane.adapt.platform;

import org.jetbrains.annotations.ApiStatus;

/**
 * The server software family Adapt runs on. Bukkit covers Spigot, Paper, Purpur and Folia.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum PlatformFamily {
  BUKKIT,
  FABRIC,
  FORGE,
  NEOFORGE;

  /** True for the mod loaders (Fabric, Forge, NeoForge). */
  public boolean isModded() {
    return this != BUKKIT;
  }
}
