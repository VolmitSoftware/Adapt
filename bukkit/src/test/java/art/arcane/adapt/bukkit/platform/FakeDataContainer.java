package art.arcane.adapt.bukkit.platform;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** In-memory container that, like the server one, only matches values stored with the same primitive type. */
final class FakeDataContainer implements PersistentDataContainer {
  final Map<NamespacedKey, Object> values = new LinkedHashMap<>();

  @Override
  public <P, C> void set(NamespacedKey key, PersistentDataType<P, C> type, C value) {
    values.put(key, type.toPrimitive(value, null));
  }

  @Override
  public void remove(NamespacedKey key) {
    values.remove(key);
  }

  @Override
  public void readFromBytes(byte[] bytes, boolean clear) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <P, C> boolean has(NamespacedKey key, PersistentDataType<P, C> type) {
    Object stored = values.get(key);
    return stored != null && type.getPrimitiveType().isInstance(stored);
  }

  @Override
  public boolean has(NamespacedKey key) {
    return values.containsKey(key);
  }

  @Override
  public <P, C> C get(NamespacedKey key, PersistentDataType<P, C> type) {
    Object stored = values.get(key);
    if (stored == null) {
      return null;
    }
    if (!type.getPrimitiveType().isInstance(stored)) {
      throw new IllegalArgumentException("stored with a different type");
    }
    return type.fromPrimitive(type.getPrimitiveType().cast(stored), null);
  }

  @Override
  public <P, C> C getOrDefault(NamespacedKey key, PersistentDataType<P, C> type, C fallback) {
    C value = get(key, type);
    return value == null ? fallback : value;
  }

  @Override
  public Set<NamespacedKey> getKeys() {
    return Set.copyOf(values.keySet());
  }

  @Override
  public boolean isEmpty() {
    return values.isEmpty();
  }

  @Override
  public void copyTo(PersistentDataContainer other, boolean replace) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PersistentDataAdapterContext getAdapterContext() {
    return FakeDataContainer::new;
  }

  @Override
  public byte[] serializeToBytes() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getSize() {
    return values.size();
  }
}
