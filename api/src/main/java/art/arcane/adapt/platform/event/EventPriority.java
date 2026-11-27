package art.arcane.adapt.platform.event;

import org.jetbrains.annotations.ApiStatus;

/** Dispatch order, lowest first. MONITOR handlers observe the final state and must not mutate it. */
@ApiStatus.AvailableSince("1.0.0")
public enum EventPriority {
  /** Runs first. */
  LOWEST,
  LOW,
  NORMAL,
  HIGH,
  HIGHEST,
  /** Runs last, to observe the outcome. Handlers here must not change the event. */
  MONITOR;

  private static final EventPriority[] VALUES = values();

  /** This priority's bit in the masks {@link AdaptEventBus#priorityMask(Class)} returns: {@code 1 << ordinal()}. */
  public int bit() {
    return 1 << ordinal();
  }

  static EventPriority at(int ordinal) {
    return VALUES[ordinal];
  }
}
