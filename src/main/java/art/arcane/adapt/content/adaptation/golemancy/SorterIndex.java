package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

final class SorterIndex {
  private final Map<ContainerId, SorterStock> stocks = new ConcurrentHashMap<>();
  private final IntSupplier capacity;
  private final LongSupplier refreshMillis;

  SorterIndex(IntSupplier capacity, LongSupplier refreshMillis) {
    this.capacity = Objects.requireNonNull(capacity);
    this.refreshMillis = Objects.requireNonNull(refreshMillis);
  }

  Optional<SorterStock> fresh(ContainerId id, long nowMillis) {
    SorterStock stock = stocks.get(id);
    return stock != null && nowMillis - stock.capturedAt() < refreshMillis.getAsLong() ? Optional.of(stock) : Optional.empty();
  }

  void put(ContainerId id, SorterStock stock) {
    int limit = Math.max(1, capacity.getAsInt());
    while (stocks.size() >= limit && !stocks.containsKey(id)) {
      evictOldest();
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

  private void evictOldest() {
    ContainerId oldest = null;
    long oldestAt = Long.MAX_VALUE;
    for (Map.Entry<ContainerId, SorterStock> entry : stocks.entrySet()) {
      if (entry.getValue().capturedAt() < oldestAt) {
        oldestAt = entry.getValue().capturedAt();
        oldest = entry.getKey();
      }
    }
    if (oldest == null) {
      return;
    }
    stocks.remove(oldest);
  }
}
