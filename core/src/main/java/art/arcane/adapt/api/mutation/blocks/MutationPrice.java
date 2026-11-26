package art.arcane.adapt.api.mutation.blocks;

import art.arcane.adapt.api.adaptation.Adaptation;

import java.util.ArrayList;
import java.util.List;

/**
 * Knowledge pricing for a mutated or upgraded adaptation, on the same cost formula as normal adaptations. A signature
 * costs what the recipe it replaces costs, so it has no tier of its own.
 */
public record MutationPrice(int baseCost, int initialCost, double costFactor, int maxLevel, int cooldownSeconds) {
  /** 13, 13 and 16 knowledge. */
  public static final MutationPrice UPGRADED = new MutationPrice(6, 4, 0.6D, 3, 0);
  /** 15, 14 and 18 knowledge, for upgrades that hand the player a craftable tool or deployable. */
  public static final MutationPrice TOOL = new MutationPrice(6, 5, 0.7D, 3, 0);
  public static final MutationPrice GRAFT = new MutationPrice(4, 3, 0.6D, 5, 0);
  public static final MutationPrice BARTER = new MutationPrice(3, 2, 0.5D, 3, 0);
  public static final MutationPrice ATTUNEMENT = new MutationPrice(6, 4, 0.6D, 3, 0);
  public static final MutationPrice CONVERGENCE = new MutationPrice(9, 7, 0.7D, 3, 45);

  public int costFor(int level) {
    return Adaptation.costFor(baseCost, initialCost, costFactor, level);
  }

  public List<Integer> costs() {
    List<Integer> costs = new ArrayList<>(maxLevel);
    for (int level = 1; level <= maxLevel; level++) {
      costs.add(costFor(level));
    }
    return List.copyOf(costs);
  }

  public int totalCost() {
    int total = 0;
    for (int cost : costs()) {
      total += cost;
    }
    return total;
  }
}
