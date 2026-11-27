package art.arcane.adapt.platform.event.inventory;

import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** One enchanting table offer. Mutable while its event is dispatched. */
@ApiStatus.AvailableSince("1.0.0")
public final class EnchantOffer {
  private Id enchantment;
  private int level;
  private int cost;

  public EnchantOffer(Id enchantment, int level, int cost) {
    this.enchantment = Objects.requireNonNull(enchantment, "enchantment");
    this.level = level;
    this.cost = cost;
  }

  /** Id of the offered enchantment. */
  public Id enchantment() {
    return enchantment;
  }

  /** Changes the offered enchantment. Must not be null. */
  public void setEnchantment(Id enchantment) {
    this.enchantment = Objects.requireNonNull(enchantment, "enchantment");
  }

  /** Level of the offered enchantment. */
  public int level() {
    return level;
  }

  /** Changes the level. Values below 1 become 1. */
  public void setLevel(int level) {
    this.level = Math.max(1, level);
  }

  /** Experience level requirement shown on the button. */
  public int cost() {
    return cost;
  }

  /** Changes the level requirement. Negative values become 0. */
  public void setCost(int cost) {
    this.cost = Math.max(0, cost);
  }
}
