package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.api.data.StoredItem;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.UUID;

public final class PlinthSlotItem {
  private final UUID id;
  private final StoredItem stored;

  private PlinthSlotItem(UUID id, StoredItem stored) {
    this.id = Objects.requireNonNull(id);
    this.stored = stored;
  }

  public static PlinthSlotItem of(UUID id, ItemStack item) {
    return new PlinthSlotItem(id, StoredItem.of(item));
  }

  /**
   * A slot as its mantle file holds it. Zero bytes are the marker of a slot whose item an older release lost.
   */
  public static PlinthSlotItem stored(UUID id, byte[] bytes) {
    return new PlinthSlotItem(id, StoredItem.stored(bytes));
  }

  public UUID id() {
    return id;
  }

  public StoredItem stored() {
    return stored;
  }

  /**
   * A copy of the item, or null while it cannot be decoded. Decodes on the first call, on the caller's thread.
   */
  public ItemStack item() {
    return stored.item();
  }

  public boolean readable() {
    return stored.readable();
  }

  public boolean lost() {
    return stored.isEmpty();
  }

  @Override
  public boolean equals(Object other) {
    return this == other || other instanceof PlinthSlotItem slot && id.equals(slot.id) && stored.sameItem(slot.stored);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "PlinthSlotItem[" + id + (lost() ? ", lost" : "") + "]";
  }
}
