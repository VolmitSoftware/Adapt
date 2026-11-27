package art.arcane.adapt.content.golem;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityPathfindEvent;
import art.arcane.adapt.platform.event.entity.ItemTransportingEntityValidateTargetEvent;

final class CopperBrainGuard {
  @Handle(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void on(ItemTransportingEntityValidateTargetEvent e) {
    if (GolemState.has(e.entity())) {
      e.setAllowed(false);
    }
  }

  @Handle(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void on(EntityPathfindEvent e) {
    EntityRef entity = e.entity();
    if (GolemBody.of(entity).orElse(null) == GolemBody.COPPER && !GolemPathing.ownsPath(entity, e.destination()) && GolemState.has(entity)) {
      e.setCancelled(true);
    }
  }
}
