package art.arcane.adapt.client.server.panel;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.common.scheduling.Tasks;

/** Moves inbound panel work onto the viewer's owning thread. */
@FunctionalInterface
public interface ViewerExecutor {
  /** Runs {@code task} on the viewer's owner, or {@code retired} (may be null) when the viewer is gone. */
  void execute(PlayerRef viewer, Runnable task, Runnable retired);

  static ViewerExecutor tasks() {
    return (PlayerRef viewer, Runnable task, Runnable retired) -> {
      if (!Tasks.runEntity(viewer, task, retired) && retired != null) {
        retired.run();
      }
    };
  }
}
