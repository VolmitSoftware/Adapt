package art.arcane.adapt.platform.permission;

import org.jetbrains.annotations.ApiStatus;

/** A node registered through {@link PermissionHost#register}; keep it to remove the node. */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface PermissionRegistration {
  /** Removes the node, unless it was already registered before this registration. Safe to call twice. */
  void remove();
}
