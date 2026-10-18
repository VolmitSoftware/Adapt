package art.arcane.adapt.api.golem;

import io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

final class CopperTransportGuard implements Listener {
  static final String EVENT_CLASS = "io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent";

  @EventHandler(priority = EventPriority.HIGH)
  public void on(ItemTransportingEntityValidateTargetEvent e) {
    if (GolemState.has(e.getEntity())) {
      e.setAllowed(false);
    }
  }
}
