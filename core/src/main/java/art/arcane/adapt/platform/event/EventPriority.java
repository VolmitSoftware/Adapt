package art.arcane.adapt.platform.event;

/** Dispatch order, lowest first. MONITOR handlers observe the final state and must not mutate it. */
public enum EventPriority {
  LOWEST,
  LOW,
  NORMAL,
  HIGH,
  HIGHEST,
  MONITOR;

  private static final EventPriority[] VALUES = values();

  public int bit() {
    return 1 << ordinal();
  }

  static EventPriority at(int ordinal) {
    return VALUES[ordinal];
  }
}
