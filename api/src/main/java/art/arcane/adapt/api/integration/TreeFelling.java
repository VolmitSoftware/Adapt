package art.arcane.adapt.api.integration;

import org.jetbrains.annotations.ApiStatus;

/**
 * What a tree felling handed to Iris asks of the adaptation that started it. Iris calls these on the felling
 * player's thread while it breaks the logs, one log at a time.
 */
@ApiStatus.Experimental
public interface TreeFelling {
  /** Whether Iris only fells while the player sneaks. */
  default boolean requiresSneaking() {
    return true;
  }

  /** Iris accepted the felling; start the cooldown or charge the activation here. */
  void onActivationAccepted();

  /** Reserves the price of the next log; false stops the felling. */
  boolean reserveLogCost();

  /** Makes the price reserved for the current log final. */
  void commitLogCost();

  /** Gives back the price reserved for the current log. */
  void refundLogCost();
}
