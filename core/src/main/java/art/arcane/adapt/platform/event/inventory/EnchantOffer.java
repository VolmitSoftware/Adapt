package art.arcane.adapt.platform.event.inventory;

import art.arcane.adapt.platform.value.Id;

import java.util.Objects;

/** One enchanting table offer. Mutable while its event is dispatched. */
public final class EnchantOffer {
  private Id enchantment;
  private int level;
  private int cost;

  public EnchantOffer(Id enchantment, int level, int cost) {
    this.enchantment = Objects.requireNonNull(enchantment, "enchantment");
    this.level = level;
    this.cost = cost;
  }

  public Id enchantment() {
    return enchantment;
  }

  public void setEnchantment(Id enchantment) {
    this.enchantment = Objects.requireNonNull(enchantment, "enchantment");
  }

  public int level() {
    return level;
  }

  public void setLevel(int level) {
    this.level = Math.max(1, level);
  }

  public int cost() {
    return cost;
  }

  public void setCost(int cost) {
    this.cost = Math.max(0, cost);
  }
}
