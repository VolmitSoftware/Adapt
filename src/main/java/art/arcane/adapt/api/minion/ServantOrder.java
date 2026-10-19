package art.arcane.adapt.api.minion;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.Objects;

public record ServantOrder(Mode mode, Location anchor, LivingEntity target, long untilMillis) {
  public static final ServantOrder FREE = new ServantOrder(Mode.FREE, null, null, 0L);

  public ServantOrder {
    Objects.requireNonNull(mode);
    anchor = anchor == null ? null : anchor.clone();
    untilMillis = target == null ? 0L : untilMillis;
  }

  public static ServantOrder follow() {
    return new ServantOrder(Mode.FOLLOW, null, null, 0L);
  }

  public static ServantOrder hold(Location anchor) {
    return new ServantOrder(Mode.HOLD, Objects.requireNonNull(anchor), null, 0L);
  }

  @Override
  public Location anchor() {
    return anchor == null ? null : anchor.clone();
  }

  public ServantOrder command(LivingEntity commanded, long until) {
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
