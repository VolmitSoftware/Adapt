package art.arcane.adapt.api.addon;

import org.jetbrains.annotations.ApiStatus;

/**
 * The life of an installed addon, as {@link AddonHandle#state()} reports it.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum AddonState {
  /** Installed, waiting for a core to start. */
  WAITING,
  /** Its content is registered in the running core. */
  ACTIVE,
  /** Its last {@code register} failed or broke a rule; nothing of it is registered. */
  REJECTED,
  /** Removed; the handle is dead. */
  UNINSTALLED
}
