package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Map;
import java.util.Objects;

/**
 * A living entity's equipment changed in one or more slots, as vanilla detects it during the entity's tick and before
 * it sends the change to clients. Read-only.
 *
 * <p>On Bukkit servers only Paper 26.x posts it.
 */
@ApiStatus.AvailableSince("1.0.0")
public class EntityEquipmentChangedEvent extends AdaptEvent {
  private final LivingRef entity;
  private final Map<EquipmentSlot, Change> changes;

  public EntityEquipmentChangedEvent(LivingRef entity, Map<EquipmentSlot, Change> changes) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.changes = Map.copyOf(changes);
  }

  /** The entity whose equipment changed. */
  public LivingRef entity() {
    return entity;
  }

  /** Each changed slot mapped to its items before and after, read-only. */
  public Map<EquipmentSlot, Change> changes() {
    return changes;
  }

  /**
   * One slot's change.
   *
   * @param oldItem the item the slot held before, empty when it was empty
   * @param newItem the item the slot holds now, empty when it is empty
   */
  public record Change(ItemRef oldItem, ItemRef newItem) {
  }
}
