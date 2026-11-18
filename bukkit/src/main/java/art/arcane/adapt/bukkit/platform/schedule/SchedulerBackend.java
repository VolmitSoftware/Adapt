package art.arcane.adapt.bukkit.platform.schedule;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/** Native scheduling primitives behind {@link BukkitScheduler}. Delays are in ticks. */
public interface SchedulerBackend {
  boolean isRegionized();

  boolean isTickThread();

  boolean owns(Entity entity);

  boolean owns(Location location);

  boolean owns(World world, int chunkX, int chunkZ);

  boolean ownsArea(Location center, double radiusX, double radiusZ);

  boolean runEntity(Entity entity, Runnable task, long delayTicks, Runnable retired);

  boolean runAt(Location location, Runnable task, long delayTicks);

  boolean runGlobal(Runnable task, long delayTicks);

  void runAsync(Runnable task, long delayTicks);
}
