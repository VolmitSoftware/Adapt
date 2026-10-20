package art.arcane.adapt.api.golem;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Predicate;

public final class GolemToolRules {
  private GolemToolRules() {
  }

  public static Choice choose(GolemToolPlan plan, ItemStack held, Predicate<GolemToolNeed> fetchable) {
    List<GolemToolNeed> needs = plan.needs();
    if (needs.isEmpty()) {
      return new Choice(Kind.FREE, -1, null);
    }
    for (int index = 0; index < needs.size(); index++) {
      GolemToolNeed need = needs.get(index);
      if (held != null && need.suits().test(held)) {
        return new Choice(Kind.HELD, index, index == 0 ? null : needs.getFirst().note());
      }
      if (fetchable.test(need)) {
        return new Choice(Kind.FETCH, index, null);
      }
    }
    return plan.free() ? new Choice(Kind.FREE, -1, needs.getFirst().note()) : new Choice(Kind.MISSING, -1, needs.getLast().note());
  }

  public static int slot(ItemStack[] contents, GolemToolNeed need) {
    int best = -1;
    int bestCost = Integer.MAX_VALUE;
    for (int index = 0; index < contents.length; index++) {
      ItemStack item = contents[index];
      if (item == null || item.getType() == Material.AIR || item.getAmount() != 1 || !need.suits().test(item)) {
        continue;
      }
      int cost = need.cost().applyAsInt(item);
      if (best < 0 || cost < bestCost) {
        best = index;
        bestCost = cost;
      }
    }
    return best;
  }

  public static Wear wear(int damage, int maxDamage, int unbreaking, double roll) {
    if (roll >= 1D / (Math.max(0, unbreaking) + 1D)) {
      return new Wear(damage, false, false);
    }
    int next = damage + 1;
    return new Wear(next, true, next >= maxDamage);
  }

  public enum Kind {
    HELD,
    FETCH,
    FREE,
    MISSING
  }

  public record Choice(Kind kind, int need, GolemToolNote note) {
  }

  public record Wear(int damage, boolean worn, boolean broken) {
  }
}
