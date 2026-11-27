package art.arcane.adapt.api.internal;

import art.arcane.adapt.platform.menu.MenuHost;
import org.jetbrains.annotations.ApiStatus;

/** The menu host a platform implements: what addons see, plus what only the engine may do. */
@ApiStatus.Internal
public interface HostMenus extends MenuHost {
  /** Closes every open Adapt menu, running their close callbacks. Called when the server stops. */
  void closeAll();
}
