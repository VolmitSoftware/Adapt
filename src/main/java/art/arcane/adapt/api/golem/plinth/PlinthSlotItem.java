package art.arcane.adapt.api.golem.plinth;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.UUID;

public final class PlinthSlotItem {
  private final UUID id;
  private final ItemStack item;

  private PlinthSlotItem(UUID id, ItemStack item) {
    this.id = Objects.requireNonNull(id);
    this.item = item;
  }

  public static PlinthSlotItem of(UUID id, ItemStack item) {
    return new PlinthSlotItem(id, Objects.requireNonNull(item));
  }

  public static PlinthSlotItem unreadable(UUID id) {
    return new PlinthSlotItem(id, null);
  }

  public UUID id() {
    return id;
  }

  public ItemStack item() {
    return item;
  }

  public boolean readable() {
    return item != null;
  }

  @Override
  public boolean equals(Object other) {
    return this == other || other instanceof PlinthSlotItem slot && id.equals(slot.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "PlinthSlotItem[" + id + (readable() ? "" : ", unreadable") + "]";
  }
}
