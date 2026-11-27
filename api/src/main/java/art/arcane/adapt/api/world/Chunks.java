package art.arcane.adapt.api.world;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.ApiStatus;

/** Chunk loading for work that has to touch a location which may be outside the loaded world. */
@ApiStatus.Experimental
public final class Chunks {
  private Chunks() {
  }

  /**
   * Runs {@code loaded} on the thread that owns {@code location} once its chunk is loaded. Runs it at once when this
   * thread owns the location and its chunk is loaded already; otherwise loads the chunk without blocking and schedules
   * the task at the location (on the global thread when the server has no regions and the location task was refused).
   */
  public static void load(Loc location, Runnable loaded) {
    AdaptScheduler scheduler = AdaptApi.scheduler();
    if ((!scheduler.isRegionized() || scheduler.owns(location))
        && location.world().isChunkLoaded(location.chunkX(), location.chunkZ())) {
      loaded.run();
      return;
    }
    location.world().loadChunkAsync(location.chunkX(), location.chunkZ()).thenAccept((Void ignored) -> {
      if (!AdaptApi.scheduler().runAt(location, loaded) && !AdaptApi.scheduler().isRegionized()) {
        AdaptApi.scheduler().runGlobal(loaded);
      }
    });
  }
}
