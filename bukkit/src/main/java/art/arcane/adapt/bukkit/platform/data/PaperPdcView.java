package art.arcane.adapt.bukkit.platform.data;

import io.papermc.paper.persistence.PersistentDataContainerView;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Set;

/** Paper's read-only item data view, which reads without copying the item meta. Loaded only on Paper. */
public record PaperPdcView(PersistentDataContainerView view) implements PdcView {
  public static PdcView of(ItemStack stack) {
    return new PaperPdcView(stack.getPersistentDataContainer());
  }

  @Override
  public <P, C> boolean has(NamespacedKey key, PersistentDataType<P, C> type) {
    return view.has(key, type);
  }

  @Override
  public boolean has(NamespacedKey key) {
    return view.has(key);
  }

  @Override
  public <P, C> C get(NamespacedKey key, PersistentDataType<P, C> type) {
    return view.get(key, type);
  }

  @Override
  public Set<NamespacedKey> getKeys() {
    return view.getKeys();
  }

  @Override
  public boolean isEmpty() {
    return view.isEmpty();
  }
}
