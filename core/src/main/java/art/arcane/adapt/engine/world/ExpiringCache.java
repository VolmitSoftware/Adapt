package art.arcane.adapt.engine.world;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Bounded cache whose entries expire a fixed time after their last write, replacing the Caffeine
 * {@code expireAfterWrite + maximumSize} caches the Bukkit build used. Every write through the cache or its
 * {@link #asMap()} view restarts the entry's expiry. When the size bound is exceeded, expired entries go first,
 * then the oldest writes.
 */
final class ExpiringCache<K, V> {
  private final ConcurrentHashMap<K, Stamped<V>> entries = new ConcurrentHashMap<>();
  private final long expiryNanos;
  private final int maximumSize;
  private final LongSupplier clock;
  private final MapView view = new MapView();

  ExpiringCache(long expiry, TimeUnit unit, int maximumSize) {
    this(expiry, unit, maximumSize, System::nanoTime);
  }

  ExpiringCache(long expiry, TimeUnit unit, int maximumSize, LongSupplier clock) {
    this.expiryNanos = unit.toNanos(expiry);
    this.maximumSize = Math.max(1, maximumSize);
    this.clock = Objects.requireNonNull(clock);
  }

  V getIfPresent(K key) {
    return view.get(key);
  }

  void put(K key, V value) {
    view.put(key, value);
  }

  void invalidate(K key) {
    entries.remove(key);
  }

  void invalidateAll() {
    entries.clear();
  }

  ConcurrentMap<K, V> asMap() {
    return view;
  }

  private Stamped<V> stamp(V value) {
    return new Stamped<>(Objects.requireNonNull(value), clock.getAsLong());
  }

  private boolean live(Stamped<V> stamped) {
    return stamped != null && clock.getAsLong() - stamped.writtenAt() < expiryNanos;
  }

  private Stamped<V> liveEntry(Object key) {
    Stamped<V> stamped = entries.get(key);
    if (stamped == null) {
      return null;
    }
    if (live(stamped)) {
      return stamped;
    }
    entries.remove(key, stamped);
    return null;
  }

  private void enforceBound() {
    if (entries.size() <= maximumSize) {
      return;
    }
    entries.entrySet().removeIf(entry -> !live(entry.getValue()));
    while (entries.size() > maximumSize) {
      Map.Entry<K, Stamped<V>> oldest = null;
      for (Map.Entry<K, Stamped<V>> entry : entries.entrySet()) {
        if (oldest == null || entry.getValue().writtenAt() - oldest.getValue().writtenAt() < 0L) {
          oldest = entry;
        }
      }
      if (oldest == null) {
        return;
      }
      entries.remove(oldest.getKey(), oldest.getValue());
    }
  }

  private record Stamped<V>(V value, long writtenAt) {
  }

  private final class MapView extends AbstractMap<K, V> implements ConcurrentMap<K, V> {
    @Override
    public V get(Object key) {
      Stamped<V> stamped = liveEntry(key);
      return stamped == null ? null : stamped.value();
    }

    @Override
    public boolean containsKey(Object key) {
      return liveEntry(key) != null;
    }

    @Override
    public V put(K key, V value) {
      Stamped<V> previous = entries.put(key, stamp(value));
      enforceBound();
      return live(previous) ? previous.value() : null;
    }

    @Override
    public V putIfAbsent(K key, V value) {
      while (true) {
        Stamped<V> current = liveEntry(key);
        if (current != null) {
          return current.value();
        }
        if (entries.putIfAbsent(key, stamp(value)) == null) {
          enforceBound();
          return null;
        }
      }
    }

    @Override
    public V remove(Object key) {
      Stamped<V> previous = entries.remove(key);
      return live(previous) ? previous.value() : null;
    }

    @Override
    public boolean remove(Object key, Object value) {
      Stamped<V> current = liveEntry(key);
      return current != null && Objects.equals(current.value(), value) && entries.remove(key, current);
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
      Stamped<V> current = liveEntry(key);
      return current != null && Objects.equals(current.value(), oldValue)
          && entries.replace(key, current, stamp(newValue));
    }

    @Override
    public V replace(K key, V value) {
      while (true) {
        Stamped<V> current = liveEntry(key);
        if (current == null) {
          return null;
        }
        if (entries.replace(key, current, stamp(value))) {
          return current.value();
        }
      }
    }

    @Override
    public void clear() {
      entries.clear();
    }

    @Override
    public int size() {
      int count = 0;
      for (Stamped<V> stamped : entries.values()) {
        if (live(stamped)) {
          count++;
        }
      }
      return count;
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
      return new AbstractSet<>() {
        @Override
        public Iterator<Map.Entry<K, V>> iterator() {
          Iterator<Map.Entry<K, Stamped<V>>> source = entries.entrySet().iterator();
          return new Iterator<>() {
            private Map.Entry<K, V> next = advance();
            private K lastKey;

            private Map.Entry<K, V> advance() {
              while (source.hasNext()) {
                Map.Entry<K, Stamped<V>> entry = source.next();
                if (live(entry.getValue())) {
                  return new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), entry.getValue().value());
                }
              }
              return null;
            }

            @Override
            public boolean hasNext() {
              return next != null;
            }

            @Override
            public Map.Entry<K, V> next() {
              if (next == null) {
                throw new NoSuchElementException();
              }
              Map.Entry<K, V> current = next;
              lastKey = current.getKey();
              next = advance();
              return current;
            }

            @Override
            public void remove() {
              if (lastKey == null) {
                throw new IllegalStateException();
              }
              entries.remove(lastKey);
              lastKey = null;
            }
          };
        }

        @Override
        public int size() {
          return MapView.this.size();
        }
      };
    }
  }
}
