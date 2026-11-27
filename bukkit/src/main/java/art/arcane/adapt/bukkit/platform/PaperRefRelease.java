package art.arcane.adapt.bukkit.platform;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Releases cached refs when Paper removes an entity from its world. Registered only on Paper. */
public final class PaperRefRelease implements Listener {
  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityRemoveFromWorldEvent event) {
    if (!(event.getEntity() instanceof Player)) {
      BukkitRefs.forget(event.getEntity());
    }
  }
}
