package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

final class SorterPlans {
  static final long PLAN_MILLIS = 60_000L;

  private final Map<UUID, Plan> plans = new ConcurrentHashMap<>();

  void restock(UUID golem, ContainerId target, ItemStack item, int amount, long nowMillis) {
    drop(plans.put(golem, new Restock(target, single(item), amount, nowMillis + PLAN_MILLIS)));
  }

  void fetch(UUID golem, SorterFetches.Request request, ItemStack item, int amount, long nowMillis) {
    drop(plans.put(golem, new Fetch(request, single(item), amount, nowMillis + PLAN_MILLIS)));
  }

  Optional<Plan> get(UUID golem, long nowMillis) {
    Plan plan = plans.get(golem);
    if (plan == null) {
      return Optional.empty();
    }
    if (plan.expiresAt() <= nowMillis) {
      if (plans.remove(golem, plan)) {
        drop(plan);
      }
      return Optional.empty();
    }
    return Optional.of(plan);
  }

  int inbound(ContainerId target, Predicate<ItemStack> same, long nowMillis) {
    int total = 0;
    for (Plan plan : plans.values()) {
      if (plan instanceof Restock restock && restock.expiresAt() > nowMillis && restock.target().equals(target) && same.test(restock.item())) {
        total += restock.amount();
      }
    }
    return total;
  }

  void forgetPlayer(UUID player) {
    for (Map.Entry<UUID, Plan> entry : plans.entrySet()) {
      Plan plan = entry.getValue();
      if (plan instanceof Fetch fetch && fetch.request().player().equals(player) && plans.remove(entry.getKey(), plan)) {
        drop(plan);
      }
    }
  }

  void forget(UUID golem) {
    drop(plans.remove(golem));
  }

  void finish(UUID golem, int delivered) {
    Plan plan = plans.remove(golem);
    if (plan instanceof Fetch fetch) {
      fetch.request().release(fetch.amount() - delivered);
    }
  }

  void sweep(long nowMillis) {
    for (Map.Entry<UUID, Plan> entry : plans.entrySet()) {
      Plan plan = entry.getValue();
      if (plan.expiresAt() <= nowMillis && plans.remove(entry.getKey(), plan)) {
        drop(plan);
      }
    }
  }

  boolean isEmpty() {
    return plans.isEmpty();
  }

  void clear() {
    plans.clear();
  }

  private static void drop(Plan plan) {
    if (plan instanceof Fetch fetch) {
      fetch.request().release(fetch.amount());
    }
  }

  private static ItemStack single(ItemStack item) {
    ItemStack copy = item.clone();
    copy.setAmount(1);
    return copy;
  }

  sealed interface Plan permits Restock, Fetch {
    long expiresAt();
  }

  record Restock(ContainerId target, ItemStack item, int amount, long expiresAt) implements Plan {
    Restock {
      Objects.requireNonNull(target);
      Objects.requireNonNull(item);
    }
  }

  record Fetch(SorterFetches.Request request, ItemStack item, int amount, long expiresAt) implements Plan {
    Fetch {
      Objects.requireNonNull(request);
      Objects.requireNonNull(item);
    }
  }
}
