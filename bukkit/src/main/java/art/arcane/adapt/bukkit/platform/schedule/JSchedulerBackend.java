package art.arcane.adapt.bukkit.platform.schedule;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/** Delegates to the plugin's Folia-aware scheduler {@link J}. */
public final class JSchedulerBackend implements SchedulerBackend {
  @Override
  public boolean isRegionized() {
    return J.isFoliaThreading();
  }

  @Override
  public boolean isTickThread() {
    return J.isPrimaryThread();
  }

  @Override
  public boolean owns(Entity entity) {
    return J.isOwnedByCurrentRegion(entity);
  }

  @Override
  public boolean owns(Location location) {
    return J.isOwnedByCurrentRegion(location);
  }

  @Override
  public boolean owns(World world, int chunkX, int chunkZ) {
    return FoliaScheduler.isOwnedByCurrentRegion(world, chunkX, chunkZ);
  }

  @Override
  public boolean ownsArea(Location center, double radiusX, double radiusZ) {
    return J.isOwnedByCurrentRegion(center, radiusX, radiusZ);
  }

  @Override
  public boolean runEntity(Entity entity, Runnable task, long delayTicks, Runnable retired) {
    return J.runEntity(entity, task, ticks(delayTicks), retired);
  }

  @Override
  public boolean runAt(Location location, Runnable task, long delayTicks) {
    return J.runAt(location, task, ticks(delayTicks));
  }

  @Override
  public boolean runGlobal(Runnable task, long delayTicks) {
    Adapt plugin = Adapt.instance;
    if (plugin == null || !plugin.isEnabled()) {
      return false;
    }
    J.s(task, ticks(delayTicks));
    return true;
  }

  @Override
  public void runAsync(Runnable task, long delayTicks) {
    J.a(task, ticks(delayTicks));
  }

  private static int ticks(long delayTicks) {
    return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, delayTicks));
  }
}
