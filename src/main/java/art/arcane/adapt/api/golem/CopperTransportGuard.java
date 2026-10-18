package art.arcane.adapt.api.golem;

import io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

final class CopperTransportGuard implements Listener {
  static final String EVENT_CLASS = "io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent";

  private final Function<Entity, Optional<GolemBrain>> brains;

  CopperTransportGuard(Function<Entity, Optional<GolemBrain>> brains) {
    this.brains = Objects.requireNonNull(brains);
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void on(ItemTransportingEntityValidateTargetEvent e) {
    Entity golem = e.getEntity();
    if (!GolemState.has(golem) || brains.apply(golem).map(brain -> brain.interacting(e.getBlock())).orElse(false)) {
      return;
    }
    e.setAllowed(false);
  }
}
