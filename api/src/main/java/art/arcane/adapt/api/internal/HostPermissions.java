package art.arcane.adapt.api.internal;

import art.arcane.adapt.platform.permission.PermissionDefault;
import art.arcane.adapt.platform.permission.PermissionHost;
import art.arcane.adapt.platform.permission.PermissionRegistration;
import org.jetbrains.annotations.ApiStatus;

/** The permission host a platform implements: what addons see, plus what only the engine may do. */
@ApiStatus.Internal
public interface HostPermissions extends PermissionHost {
  /**
   * Registers a node with the default that applies when nothing sets it explicitly. Returns false when the node was
   * already registered, by this host or anyone else, and leaves it as it is.
   */
  boolean add(String node, String description, PermissionDefault fallback);

  /** Removes a node this host registered; a node it did not register, or an unknown one, is left alone. */
  void unregister(String node);

  @Override
  default PermissionRegistration register(String node, String description, PermissionDefault fallback) {
    if (!add(node, description, fallback)) {
      return () -> {
      };
    }
    return new PermissionRegistration() {
      private boolean removed;

      @Override
      public synchronized void remove() {
        if (!removed) {
          removed = true;
          unregister(node);
        }
      }
    };
  }
}
