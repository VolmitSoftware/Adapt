package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.Map;
import java.util.Objects;

/** A living entity's equipment changed in one or more slots during its tick. Read-only. */
public class EntityEquipmentChangedEvent extends AdaptEvent {
  private final LivingRef entity;
  private final Map<EquipmentSlot, Change> changes;

  public EntityEquipmentChangedEvent(LivingRef entity, Map<EquipmentSlot, Change> changes) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.changes = Map.copyOf(changes);
  }

  public LivingRef entity() {
    return entity;
  }

  public Map<EquipmentSlot, Change> changes() {
    return changes;
  }

  public record Change(ItemRef oldItem, ItemRef newItem) {
  }
}
