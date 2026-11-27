package art.arcane.adapt.engine.fx;

/** Recent server ticks per second, used by {@link FxBudget} to shed effects under load. */
public interface TpsPort {
  TpsPort NONE = () -> 20.0D;

  /** One-minute average TPS, or 20 when the platform cannot tell. */
  double recentTps();
}
