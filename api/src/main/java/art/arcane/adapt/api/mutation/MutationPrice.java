package art.arcane.adapt.api.mutation;

import art.arcane.adapt.api.content.Adaptation;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Knowledge pricing of a mutated or upgraded adaptation, on the cost formula of ordinary adaptations. A signature
 * costs what the recipe it replaces costs.
 *
 * @param baseCost        as {@link art.arcane.adapt.api.content.AdaptationConfig#baseCost}, at least 0
 * @param initialCost     as {@link art.arcane.adapt.api.content.AdaptationConfig#initialCost}, at least 0
 * @param costFactor      as {@link art.arcane.adapt.api.content.AdaptationConfig#costFactor}, finite and at least 0
 * @param maxLevel        the number of levels, at least 1
 * @param cooldownSeconds the cooldown shown in a generated description; the cooldown a run actually waits comes from
 *                        the server's mutation config
 */
@ApiStatus.Experimental
public record MutationPrice(int baseCost, int initialCost, double costFactor, int maxLevel, int cooldownSeconds) {
  /** For an ordinary upgrade. */
  public static final MutationPrice UPGRADED = new MutationPrice(6, 4, 0.6D, 3, 0);
  /** For an upgrade that hands the player a craftable tool or deployable. */
  public static final MutationPrice TOOL = new MutationPrice(6, 5, 0.7D, 3, 0);
  /** For {@link Recipe#GRAFT} and {@link Recipe#ECHO}. */
  public static final MutationPrice GRAFT = new MutationPrice(4, 3, 0.6D, 5, 0);
  /** For {@link Recipe#BARTER}. */
  public static final MutationPrice BARTER = new MutationPrice(3, 2, 0.5D, 3, 0);
  /** For {@link Recipe#ATTUNEMENT}. */
  public static final MutationPrice ATTUNEMENT = new MutationPrice(6, 4, 0.6D, 3, 0);
  /** For {@link Recipe#CONVERGENCE}; its description names a 45 second cooldown. */
  public static final MutationPrice CONVERGENCE = new MutationPrice(9, 7, 0.7D, 3, 45);

  /**
   * @throws IllegalArgumentException when a cost or the cooldown is negative, the factor is negative or not finite,
   *                                  or {@code maxLevel} is below 1
   */
  public MutationPrice {
    if (baseCost < 0 || initialCost < 0 || maxLevel < 1 || cooldownSeconds < 0 || !Double.isFinite(costFactor)
        || costFactor < 0D) {
      throw new IllegalArgumentException("Invalid mutation price");
    }
  }

  /** Knowledge for exactly this level step, by {@link Adaptation#costFor(int, int, double, int)}. */
  public int costFor(int level) {
    return Adaptation.costFor(baseCost, initialCost, costFactor, level);
  }

  /** The cost of each level, first level first. */
  public List<Integer> costs() {
    List<Integer> costs = new ArrayList<>(maxLevel);
    for (int level = 1; level <= maxLevel; level++) {
      costs.add(costFor(level));
    }
    return List.copyOf(costs);
  }
}
