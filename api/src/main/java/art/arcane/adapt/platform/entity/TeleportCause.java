package art.arcane.adapt.platform.entity;

import org.jetbrains.annotations.ApiStatus;

/**
 * Why a teleport happens, named like Bukkit's {@code PlayerTeleportEvent.TeleportCause}.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum TeleportCause {
  /** A plugin or mod moved the entity; modded also uses it when no cause is given. */
  PLUGIN,
  COMMAND,
  ENDER_PEARL,
  CHORUS_FRUIT,
  NETHER_PORTAL,
  END_PORTAL,
  END_GATEWAY,
  /** A spectator teleported through the spectator menu. */
  SPECTATE,
  DISMOUNT,
  EXIT_BED,
  /** Any cause the platform cannot name. */
  UNKNOWN
}
