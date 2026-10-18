package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

final class SorterIndex {
  private static final int EVICTION_DIVISOR = 4;

  private final Map<ContainerId, SorterStock> stocks = new ConcurrentHashMap<>();
  private final IntSupplier capacity;
  private final LongSupplier refreshMillis;

  SorterIndex(IntSupplier capacity, LongSupplier refreshMillis) {
    this.capacity = Objects.requireNonNull(capacity);
    this.refreshMillis = Objects.requireNonNull(refreshMillis);
  }

  Optional<SorterStock> fresh(ContainerId id, long nowMillis, long maxAgeMillis) {
    SorterStock stock = stocks.get(id);
    return stock != null && nowMillis - stock.capturedAt() < maxAgeMillis ? Optional.of(stock) : Optional.empty();
  }

  void put(ContainerId id, SorterStock stock) {
    int limit = Math.max(1, capacity.getAsInt());
    if (stocks.size() >= limit && !stocks.containsKey(id)) {
      evictOldest(Math.max(1, limit / EVICTION_DIVISOR));
    }
    stocks.put(id, stock);
  }

  void invalidate(ContainerId id) {
    stocks.remove(id);
  }

  void invalidate(Inventory inventory) {
    if (stocks.isEmpty() || inventory == null) {
      return;
    }
    ContainerId.of(inventory).ifPresent(stocks::remove);
  }

  void sweep(long nowMillis) {
    long refresh = refreshMillis.getAsLong();
    stocks.values().removeIf(stock -> nowMillis - stock.capturedAt() >= refresh);
  }

  boolean isEmpty() {
    return stocks.isEmpty();
  }

  void clear() {
    stocks.clear();
  }

  private void evictOldest(int count) {
    List<Map.Entry<ContainerId, SorterStock>> entries = new ArrayList<>(stocks.entrySet());
    entries.sort((first, second) -> Long.compare(first.getValue().capturedAt(), second.getValue().capturedAt()));
    for (int index = 0; index < Math.min(count, entries.size()); index++) {
      Map.Entry<ContainerId, SorterStock> oldest = entries.get(index);
      stocks.remove(oldest.getKey(), oldest.getValue());
    }
  }
}
