package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** A tool or armour piece is about to lose durability. Cancelling keeps the durability. Mutable: damage, cancelled. */
@ApiStatus.AvailableSince("1.0.0")
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

  /** The item losing durability. */
  public ItemRef item() {
    return item;
  }

  /** Durability points about to be lost, after Unbreaking. */
  public int damage() {
    return damage;
  }

  /** Changes the durability lost, clamped at 0. */
  public void setDamage(int damage) {
    this.damage = Math.max(0, damage);
  }

  /** Damage before Unbreaking and other plugins changed it. */
  public int originalDamage() {
    return originalDamage;
  }
}
