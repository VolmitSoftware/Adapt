package art.arcane.adapt.api.internal;

import art.arcane.adapt.platform.AdaptScheduler;
import org.jetbrains.annotations.ApiStatus;

/** The scheduler a platform host implements: what addons see, plus what only the engine may do. */
@ApiStatus.Internal
public interface HostScheduler extends AdaptScheduler {
  /** Cancels every task scheduled through this scheduler. Called on shutdown. */
  void cancelAll();
}
