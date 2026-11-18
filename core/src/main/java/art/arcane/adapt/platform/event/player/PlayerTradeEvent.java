package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.Objects;

/**
 * A player completes a trade with a villager or wandering trader. Mutable: rewardExp,
 * increaseTradeUses, cancelled.
 */
public class PlayerTradeEvent extends PlayerEvent {
  private final EntityRef merchant;
  private final ItemRef result;
  private boolean rewardExp;
  private boolean increaseTradeUses;

  public PlayerTradeEvent(PlayerRef player, EntityRef merchant, ItemRef result, boolean rewardExp, boolean increaseTradeUses) {
    super(player);
    this.merchant = Objects.requireNonNull(merchant, "merchant");
    this.result = Objects.requireNonNull(result, "result");
    this.rewardExp = rewardExp;
    this.increaseTradeUses = increaseTradeUses;
  }

  public EntityRef merchant() {
    return merchant;
  }

  public ItemRef result() {
    return result;
  }

  public boolean rewardExp() {
    return rewardExp;
  }

  public void setRewardExp(boolean rewardExp) {
    this.rewardExp = rewardExp;
  }

  public boolean increaseTradeUses() {
    return increaseTradeUses;
  }

  public void setIncreaseTradeUses(boolean increaseTradeUses) {
    this.increaseTradeUses = increaseTradeUses;
  }
}
