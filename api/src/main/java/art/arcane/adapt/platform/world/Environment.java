package art.arcane.adapt.platform.world;

import org.jetbrains.annotations.ApiStatus;

/**
 * Dimension kind of a world, named like Bukkit's {@code World.Environment}.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum Environment {
  NORMAL,
  NETHER,
  THE_END,
  /** A dimension other than the overworld, the nether and the end; modded reports every such dimension this way. */
  CUSTOM
}
