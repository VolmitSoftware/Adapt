package art.arcane.adapt.api.projectile;

import art.arcane.adapt.platform.data.DataCodec;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.data.DataType;
import art.arcane.adapt.platform.data.DataValue;
import art.arcane.adapt.platform.value.Id;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Projectile data keys that other projectile modifiers may coexist with. A projectile is unclaimed when every
 * data key on it is cooperative or owned by the caller.
 */
public final class ProjectileCooperativeKeys {
  private static final Set<Id> COOPERATIVE = ConcurrentHashMap.newKeySet();

  private ProjectileCooperativeKeys() {
  }

  public static void register(Id... keys) {
    for (Id key : keys) {
      COOPERATIVE.add(Objects.requireNonNull(key));
    }
  }

  public static boolean isCooperative(Id key) {
    return COOPERATIVE.contains(key);
  }

  public static boolean isUnclaimed(DataHolder data, Id... ownedKeys) {
    if (data == null) {
      return false;
    }

    Set<Id> keys = data.keys();
    if (keys == null || keys.isEmpty()) {
      return true;
    }

    for (Id key : keys) {
      if (!COOPERATIVE.contains(key) && !isOwned(key, ownedKeys)) {
        return false;
      }
    }

    return true;
  }

  /** The cooperative entries stored on {@code data}, keyed by id, for {@link #restore} onto a replacement projectile. */
  public static Map<Id, DataValue> snapshotCooperative(DataHolder data) {
    Map<Id, DataValue> snapshot = new LinkedHashMap<>();
    for (Id key : data.keys()) {
      if (!COOPERATIVE.contains(key)) {
        continue;
      }
      for (DataType type : DataType.values()) {
        DataKey<DataValue> raw = DataKey.of(key, new RawCodec(type));
        if (data.has(raw)) {
          DataValue value = data.get(raw);
          if (value != null) {
            snapshot.put(key, value);
          }
          break;
        }
      }
    }
    return snapshot;
  }

  /** Writes entries taken by {@link #snapshotCooperative} onto {@code data}, replacing existing ones. */
  public static void restore(DataHolder data, Map<Id, DataValue> snapshot) {
    for (Map.Entry<Id, DataValue> entry : snapshot.entrySet()) {
      data.set(DataKey.of(entry.getKey(), new RawCodec(entry.getValue().type())), entry.getValue());
    }
  }

  private record RawCodec(DataType type) implements DataCodec<DataValue> {
    @Override
    public DataValue encode(DataValue value) {
      return value;
    }

    @Override
    public DataValue decode(DataValue value) {
      return value;
    }
  }

  private static boolean isOwned(Id key, Id[] ownedKeys) {
    for (Id owned : ownedKeys) {
      if (owned != null && owned.equals(key)) {
        return true;
      }
    }

    return false;
  }
}
