package art.arcane.adapt.testkit;

import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.data.DataValue;
import art.arcane.adapt.platform.value.Id;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** In-memory data holder keyed by id; values pass through their codec like real storage. */
public final class MemoryData implements DataHolder {
  private final Map<Id, DataValue> values = new HashMap<>();

  @Override
  public <T> T get(DataKey<T> key) {
    DataValue value = values.get(key.id());
    return value == null ? null : key.codec().decode(value);
  }

  @Override
  public boolean has(DataKey<?> key) {
    return values.containsKey(key.id());
  }

  @Override
  public boolean hasAny(Id id) {
    return values.containsKey(id);
  }

  @Override
  public <T> void set(DataKey<T> key, T value) {
    if (value == null) {
      values.remove(key.id());
    } else {
      values.put(key.id(), key.codec().encode(value));
    }
  }

  @Override
  public void remove(Id id) {
    values.remove(id);
  }

  @Override
  public Set<Id> keys() {
    return Set.copyOf(values.keySet());
  }

  @Override
  public boolean isEmpty() {
    return values.isEmpty();
  }

  /** Copies every stored value into {@code target}, as copying a real item copies its data. */
  public void copyInto(MemoryData target) {
    target.values.putAll(values);
  }
}
