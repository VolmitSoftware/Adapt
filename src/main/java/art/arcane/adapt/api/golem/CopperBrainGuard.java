package art.arcane.adapt.api.golem;

import com.destroystokyo.paper.event.entity.EntityPathfindEvent;
import io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

final class CopperBrainGuard implements Listener {
  static final String EVENT_CLASS = "io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent";

  @EventHandler(priority = EventPriority.HIGH)
  public void on(ItemTransportingEntityValidateTargetEvent e) {
    if (GolemState.has(e.getEntity())) {
      e.setAllowed(false);
    }
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void on(EntityPathfindEvent e) {
    Entity entity = e.getEntity();
    if (entity instanceof CopperGolem && !GolemPathing.ownsPath(entity, e.getLoc()) && GolemState.has(entity)) {
      e.setCancelled(true);
    }
  }
}
