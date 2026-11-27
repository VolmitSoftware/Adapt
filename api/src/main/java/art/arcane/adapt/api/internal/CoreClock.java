package art.arcane.adapt.api.internal;

import art.arcane.adapt.api.AdaptClock;
import org.jetbrains.annotations.ApiStatus;

/** The clock {@code AdaptApi.clock()} returns: the running core's, or system time and tick 0 without one. */
@ApiStatus.Internal
final class CoreClock implements AdaptClock {
  static final CoreClock INSTANCE = new CoreClock();

  private CoreClock() {
  }

  @Override
  public long millis() {
    EngineBridge bridge = Engine.current();
    return bridge == null ? System.currentTimeMillis() : bridge.clock().millis();
  }

  @Override
  public long tick() {
    EngineBridge bridge = Engine.current();
    return bridge == null ? 0L : bridge.clock().tick();
  }

  @Override
  public double loadPercent() {
    EngineBridge bridge = Engine.current();
    return bridge == null ? 0D : bridge.clock().loadPercent();
  }
}
