package art.arcane.adapt.api.data;

import art.arcane.adapt.Adapt;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * Opens a world's mantle data when the world loads, so a pending Spatial migration runs at startup
 * or world load and not inside the first block event that asks for the data.
 */
public final class WorldDataLoader implements Listener {
  public static void load(Iterable<World> worlds) {
    for (World world : worlds) {
      load(world);
    }
  }

  private static void load(World world) {
    try {
      WorldData.of(world);
    } catch (RuntimeException failure) {
      Adapt.error("Could not open the mantle data of world " + world.getName(), failure);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void on(WorldLoadEvent event) {
    load(event.getWorld());
  }
}
