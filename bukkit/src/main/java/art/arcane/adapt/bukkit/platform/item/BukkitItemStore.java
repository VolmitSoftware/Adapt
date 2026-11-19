package art.arcane.adapt.bukkit.platform.item;

import art.arcane.adapt.bukkit.platform.BukkitResolve;
import art.arcane.adapt.bukkit.platform.data.PdcStore;
import io.papermc.paper.persistence.PersistentDataContainerView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.function.Consumer;

/** Persistent data of an item stack; edits go through a fresh meta written back to the stack. */
record BukkitItemStore(ItemStack stack) implements PdcStore {
  @Override
  public PersistentDataContainerView read() {
    return BukkitResolve.isAir(stack.getType()) ? null : stack.getPersistentDataContainer();
  }

  @Override
  public void write(Consumer<PersistentDataContainer> edit) {
    if (BukkitResolve.isAir(stack.getType())) {
      return;
    }
    ItemMeta meta = stack.getItemMeta();
    if (meta == null) {
      return;
    }
    edit.accept(meta.getPersistentDataContainer());
    stack.setItemMeta(meta);
  }
}
