package art.arcane.adapt.bukkit.platform.data;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Set;

/** Read access to a persistent data container, so Paper's read-only item view and Bukkit's container read the same way. */
public interface PdcView {
  <P, C> boolean has(NamespacedKey key, PersistentDataType<P, C> type);

  boolean has(NamespacedKey key);

  <P, C> C get(NamespacedKey key, PersistentDataType<P, C> type);

  Set<NamespacedKey> getKeys();

  boolean isEmpty();

  /** A view of {@code container}, or null when there is no container. */
  static PdcView of(PersistentDataContainer container) {
    return container == null ? null : new PdcView() {
      @Override
      public <P, C> boolean has(NamespacedKey key, PersistentDataType<P, C> type) {
        return container.has(key, type);
      }

      @Override
      public boolean has(NamespacedKey key) {
        return container.has(key);
      }

      @Override
      public <P, C> C get(NamespacedKey key, PersistentDataType<P, C> type) {
        return container.get(key, type);
      }

      @Override
      public Set<NamespacedKey> getKeys() {
        return container.getKeys();
      }

      @Override
      public boolean isEmpty() {
        return container.isEmpty();
      }
    };
  }
}
