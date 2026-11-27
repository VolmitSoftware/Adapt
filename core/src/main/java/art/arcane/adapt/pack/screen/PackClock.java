package art.arcane.adapt.pack.screen;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.math.M;

/** Time and delayed work for pack screens: the seal's arming window and coalesced refreshes. */
public interface PackClock {
  PackClock SERVER = new PackClock() {
    @Override
    public long now() {
      return M.ms();
    }

    @Override
    public void later(PlayerRef viewer, int ticks, Runnable task) {
      Tasks.runEntity(viewer, task, ticks);
    }
  };

  long now();

  /** Runs {@code task} on the viewer's owning thread after {@code ticks}. */
  void later(PlayerRef viewer, int ticks, Runnable task);
}
