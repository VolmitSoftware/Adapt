package art.arcane.adapt.platform.permission;

import org.jetbrains.annotations.ApiStatus;

/**
 * Who holds a registered permission node when nothing sets it for them explicitly.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum PermissionDefault {
  /** Everyone. */
  TRUE,
  /** No one. */
  FALSE,
  /** Server operators only. */
  OP,
  /** Everyone except server operators. */
  NOT_OP
}
