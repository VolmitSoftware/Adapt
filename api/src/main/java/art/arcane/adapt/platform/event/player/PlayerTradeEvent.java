package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A player completes a trade with a villager or wandering trader. On Bukkit it needs Paper. Cancelling undoes the
 * trade; on mod platforms only while the result is still on the cursor. Mutable: rewardExp, increaseTradeUses,
 * cancelled.
 */
@ApiStatus.AvailableSince("1.0.0")
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

  /** The villager or wandering trader. */
  public EntityRef merchant() {
    return merchant;
  }

  /** The item the trade gives. */
  public ItemRef result() {
    return result;
  }

  /** True when the trade grants its experience reward. */
  public boolean rewardExp() {
    return rewardExp;
  }

  /** Changes whether the experience reward is granted. */
  public void setRewardExp(boolean rewardExp) {
    this.rewardExp = rewardExp;
  }

  /** True when the trade counts toward the offer's use limit. */
  public boolean increaseTradeUses() {
    return increaseTradeUses;
  }

  /** Changes whether the trade counts toward the use limit. */
  public void setIncreaseTradeUses(boolean increaseTradeUses) {
    this.increaseTradeUses = increaseTradeUses;
  }
}
