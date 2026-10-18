package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.node.NodeKey;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

final class SorterFetches {
  private final Map<UUID, Request> requests = new ConcurrentHashMap<>();
  private final LongSupplier timeoutMillis;

  SorterFetches(LongSupplier timeoutMillis) {
    this.timeoutMillis = Objects.requireNonNull(timeoutMillis);
  }

  Request request(UUID player, NodeKey input, ItemStack prototype, int amount, long nowMillis) {
    ItemStack item = prototype.clone();
    item.setAmount(1);
    Request request = new Request(player, input, item, Math.max(1, amount), nowMillis, nowMillis + timeoutMillis.getAsLong());
    requests.put(player, request);
    return request;
  }

  List<Request> open(NodeKey input, long nowMillis) {
    List<Request> open = new ArrayList<>();
    for (Request request : requests.values()) {
      if (request.input().equals(input) && request.open(nowMillis)) {
        open.add(request);
      }
    }
    open.sort(Comparator.comparingLong(Request::createdAt));
    return open;
  }

  Optional<Request> next(NodeKey input, ItemStack prototype, long nowMillis) {
    for (Request request : open(input, nowMillis)) {
      if (request.item().isSimilar(prototype)) {
        return Optional.of(request);
      }
    }
    return Optional.empty();
  }

  void cancel(UUID player) {
    requests.remove(player);
  }

  void sweep(long nowMillis) {
    requests.values().removeIf(request -> request.expired(nowMillis));
  }

  boolean isEmpty() {
    return requests.isEmpty();
  }

  void clear() {
    requests.clear();
  }

  static final class Request {
    private final UUID player;
    private final NodeKey input;
    private final ItemStack item;
    private final AtomicInteger remaining;
    private final long createdAt;
    private final long expiresAt;

    private Request(UUID player, NodeKey input, ItemStack item, int amount, long createdAt, long expiresAt) {
      this.player = Objects.requireNonNull(player);
      this.input = Objects.requireNonNull(input);
      this.item = item;
      this.remaining = new AtomicInteger(amount);
      this.createdAt = createdAt;
      this.expiresAt = expiresAt;
    }

    UUID player() {
      return player;
    }

    NodeKey input() {
      return input;
    }

    ItemStack item() {
      return item.clone();
    }

    int remaining() {
      return remaining.get();
    }

    long createdAt() {
      return createdAt;
    }

    int reserve(int maximum) {
      while (true) {
        int left = remaining.get();
        int taking = Math.min(left, Math.max(0, maximum));
        if (taking <= 0) {
          return 0;
        }
        if (remaining.compareAndSet(left, left - taking)) {
          return taking;
        }
      }
    }

    void release(int amount) {
      if (amount > 0) {
        remaining.addAndGet(amount);
      }
    }

    private boolean open(long nowMillis) {
      return remaining.get() > 0 && !expired(nowMillis);
    }

    private boolean expired(long nowMillis) {
      return nowMillis >= expiresAt;
    }
  }
}
