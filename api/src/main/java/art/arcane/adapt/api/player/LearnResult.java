package art.arcane.adapt.api.player;

import org.jetbrains.annotations.ApiStatus;

/** The outcome of a learn or unlearn. Adapt creates results; new accessors may be added. */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface LearnResult {
  /** How the call ended. */
  Status status();

  /** The level before the call. */
  int fromLevel();

  /** The level after it; equal to {@link #fromLevel()} unless the status is a success. */
  int toLevel();

  /** Knowledge spent, or refunded for an unlearn. */
  long knowledge();

  /** Whether the level changed: the status is {@link Status#LEARNED} or {@link Status#UNLEARNED}. */
  default boolean succeeded() {
    return status() == Status.LEARNED || status() == Status.UNLEARNED;
  }

  /** How a learn or unlearn ended. A {@code switch} over this enum needs a default branch: minor releases add constants. */
  enum Status {
    /** The level was raised. */
    LEARNED,
    /** The level was lowered, or the adaptation dropped. */
    UNLEARNED,
    /** The target level is the current level, or on the wrong side of it. */
    NO_CHANGE,
    /** The id is not a registered, enabled adaptation, or its skill line is not available to the player. */
    UNAVAILABLE,
    /** The new level would take the player past their maximum power. */
    INSUFFICIENT_POWER,
    /** The skill line holds less unspent knowledge than the price. */
    INSUFFICIENT_KNOWLEDGE,
    /** The adaptation also costs money and the player cannot pay it. */
    INSUFFICIENT_FUNDS,
    /** The adaptation costs money and no economy provider is available. */
    ECONOMY_UNAVAILABLE,
    /** The economy provider failed to take the money. */
    ECONOMY_FAILED,
    /** The adaptation is permanent and cannot be unlearned. */
    PERMANENT,
    /** An adaptation this one requires is not learned at the needed level. */
    REQUIREMENTS_MISSING,
    /** Another learned adaptation requires this one at its current level. */
    REQUIRED_BY_DEPENDENTS,
    /** A listener cancelled the event. */
    CANCELLED
  }
}
