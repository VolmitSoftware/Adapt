package art.arcane.adapt.api.minion;

import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.value.Loc;

import java.util.Objects;

public record ServantOrder(Mode mode, Loc anchor, LivingRef target, long untilMillis) {
  public static final ServantOrder FREE = new ServantOrder(Mode.FREE, null, null, 0L);

  public ServantOrder {
    Objects.requireNonNull(mode);
    untilMillis = target == null ? 0L : untilMillis;
  }

  public static ServantOrder follow() {
    return new ServantOrder(Mode.FOLLOW, null, null, 0L);
  }

  public static ServantOrder hold(Loc anchor) {
    return new ServantOrder(Mode.HOLD, Objects.requireNonNull(anchor), null, 0L);
  }

  public ServantOrder command(LivingRef commanded, long until) {
    return new ServantOrder(mode, anchor, Objects.requireNonNull(commanded), until);
  }

  public ServantOrder released() {
    return target == null ? this : new ServantOrder(mode, anchor, null, 0L);
  }

  public boolean commanded() {
    return target != null;
  }

  public boolean commandHolds(long now, boolean targetReachable) {
    return target != null && targetReachable && now < untilMillis;
  }

  public boolean isFree() {
    return mode == Mode.FREE && target == null;
  }

  public enum Mode {
    FREE,
    FOLLOW,
    HOLD
  }
}
