package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.Objects;

/** A tool or armour piece is about to lose durability. Mutable: damage, cancelled. */
public class PlayerItemDamageEvent extends PlayerEvent {
  private final ItemRef item;
  private final int originalDamage;
  private int damage;

  public PlayerItemDamageEvent(PlayerRef player, ItemRef item, int damage, int originalDamage) {
    super(player);
    this.item = Objects.requireNonNull(item, "item");
    this.damage = damage;
    this.originalDamage = originalDamage;
  }

  public ItemRef item() {
    return item;
  }

  public int damage() {
    return damage;
  }

  public void setDamage(int damage) {
    this.damage = Math.max(0, damage);
  }

  /** Damage before Unbreaking and other plugins changed it. */
  public int originalDamage() {
    return originalDamage;
  }
}
