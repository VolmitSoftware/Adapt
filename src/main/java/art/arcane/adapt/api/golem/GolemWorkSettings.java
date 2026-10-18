package art.arcane.adapt.api.golem;

public record GolemWorkSettings(int taskLeaseSeconds, double baseWorkRadius, int stuckTimeoutSeconds) {
  public static final int MIN_LEASE_SECONDS = 5;
  public static final int MAX_LEASE_SECONDS = 600;
  public static final double MIN_WORK_RADIUS = 4D;
  public static final double MAX_WORK_RADIUS = 128D;
  public static final int MIN_STUCK_SECONDS = 1;
  public static final int MAX_STUCK_SECONDS = 60;

  private static final double DEFAULT_WORK_RADIUS = 24D;
  private static final GolemWorkSettings DEFAULTS = new GolemWorkSettings(20, DEFAULT_WORK_RADIUS, 4);

  public GolemWorkSettings {
    taskLeaseSeconds = Math.clamp(taskLeaseSeconds, MIN_LEASE_SECONDS, MAX_LEASE_SECONDS);
    baseWorkRadius = Double.isFinite(baseWorkRadius) ? Math.clamp(baseWorkRadius, MIN_WORK_RADIUS, MAX_WORK_RADIUS) : DEFAULT_WORK_RADIUS;
    stuckTimeoutSeconds = Math.clamp(stuckTimeoutSeconds, MIN_STUCK_SECONDS, MAX_STUCK_SECONDS);
  }

  public static GolemWorkSettings defaults() {
    return DEFAULTS;
  }

  public long leaseMillis() {
    return taskLeaseSeconds * 1_000L;
  }

  public long stuckMillis() {
    return stuckTimeoutSeconds * 1_000L;
  }
}
