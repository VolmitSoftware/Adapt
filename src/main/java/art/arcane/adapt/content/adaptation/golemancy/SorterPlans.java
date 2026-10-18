package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class SorterPlans {
  static final long PLAN_MILLIS = 60_000L;

  private final Map<UUID, Plan> plans = new ConcurrentHashMap<>();

  void restock(UUID golem, ContainerId target, ItemStack item, long nowMillis) {
    plans.put(golem, new Restock(target, single(item), nowMillis + PLAN_MILLIS));
  }

  void fetch(UUID golem, UUID player, ItemStack item, int amount, long nowMillis) {
    plans.put(golem, new Fetch(player, single(item), amount, nowMillis + PLAN_MILLIS));
  }

  Optional<Plan> get(UUID golem, long nowMillis) {
    Plan plan = plans.get(golem);
    if (plan == null) {
      return Optional.empty();
    }
    if (plan.expiresAt() <= nowMillis) {
      plans.remove(golem, plan);
      return Optional.empty();
    }
    return Optional.of(plan);
  }

  void forget(UUID golem) {
    plans.remove(golem);
  }

  void sweep(long nowMillis) {
    plans.values().removeIf(plan -> plan.expiresAt() <= nowMillis);
  }

  boolean isEmpty() {
    return plans.isEmpty();
  }

  void clear() {
    plans.clear();
  }

  private static ItemStack single(ItemStack item) {
    ItemStack copy = item.clone();
    copy.setAmount(1);
    return copy;
  }

  sealed interface Plan permits Restock, Fetch {
    long expiresAt();
  }

  record Restock(ContainerId target, ItemStack item, long expiresAt) implements Plan {
    Restock {
      Objects.requireNonNull(target);
      Objects.requireNonNull(item);
    }
  }

  record Fetch(UUID player, ItemStack item, int amount, long expiresAt) implements Plan {
    Fetch {
      Objects.requireNonNull(player);
      Objects.requireNonNull(item);
    }
  }
}
