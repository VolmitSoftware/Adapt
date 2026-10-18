package art.arcane.adapt.api.golem;

public record GolemBrainSettings(
    int brainIntervalTicks,
    double followTeleportDistance,
    double leashRadius,
    int idleReturnSeconds,
    int copperRepathTicks) {
  public static final int MIN_BRAIN_INTERVAL_TICKS = 2;
  public static final int MAX_BRAIN_INTERVAL_TICKS = 20;
  public static final double MIN_FOLLOW_TELEPORT_DISTANCE = 8D;
  public static final double MIN_LEASH_RADIUS = 8D;
  public static final int MIN_COPPER_REPATH_TICKS = 1;
  public static final int MAX_COPPER_REPATH_TICKS = 20;

  private static final double DEFAULT_FOLLOW_TELEPORT_DISTANCE = 32D;
  private static final double DEFAULT_LEASH_RADIUS = 32D;
  private static final GolemBrainSettings DEFAULTS = new GolemBrainSettings(5, DEFAULT_FOLLOW_TELEPORT_DISTANCE, DEFAULT_LEASH_RADIUS, 8, 10);

  public GolemBrainSettings {
    brainIntervalTicks = Math.clamp(brainIntervalTicks, MIN_BRAIN_INTERVAL_TICKS, MAX_BRAIN_INTERVAL_TICKS);
    followTeleportDistance = Double.isFinite(followTeleportDistance)
        ? Math.max(MIN_FOLLOW_TELEPORT_DISTANCE, followTeleportDistance)
        : DEFAULT_FOLLOW_TELEPORT_DISTANCE;
    leashRadius = Double.isFinite(leashRadius) ? Math.max(MIN_LEASH_RADIUS, leashRadius) : DEFAULT_LEASH_RADIUS;
    idleReturnSeconds = Math.max(0, idleReturnSeconds);
    copperRepathTicks = Math.clamp(copperRepathTicks, MIN_COPPER_REPATH_TICKS, MAX_COPPER_REPATH_TICKS);
  }

  public static GolemBrainSettings defaults() {
    return DEFAULTS;
  }

  public long idleReturnMillis() {
    return idleReturnSeconds * 1_000L;
  }
}
