package art.arcane.adapt.api.golem;

import java.util.Objects;

final class GolemBrainMachine {
  static final double FOLLOW_START_DISTANCE = 4D;
  static final double FOLLOW_STOP_DISTANCE = 2.5D;
  static final double ARRIVE_DISTANCE = 1.5D;
  static final double RESUME_DISTANCE = 2.5D;
  static final double WANDER_RADIUS = 4D;
  static final double NEAR_HOME_DISTANCE = WANDER_RADIUS + ARRIVE_DISTANCE;
  static final long WANDER_INTERVAL_MILLIS = 6_000L;
  static final long TELEPORT_COOLDOWN_MILLIS = 2_000L;

  private GolemBrainState state = GolemBrainState.IDLE;
  private boolean primed;
  private boolean moving;
  private boolean leashBroken;
  private long idleSince;
  private boolean wandered;
  private long lastWanderAt;
  private boolean teleported;
  private long lastTeleportAt;

  Decision decide(Senses senses, GolemBrainSettings settings) {
    long now = senses.nowMillis();
    if (!primed) {
      primed = true;
      idleSince = now;
    }
    updateLeash(senses, settings.leashRadius());
    if (senses.hasTarget() && !leashBroken) {
      return settle(now, GolemBrainState.COMBAT, Desire.STAND);
    }
    return switch (senses.mode()) {
      case FOLLOW -> follow(senses, settings);
      case STAY -> stay(senses);
      case HOME -> home(senses);
      case WORK -> work(senses, settings);
    };
  }

  GolemBrainState state() {
    return state;
  }

  boolean combatSuppressed() {
    return leashBroken;
  }

  void working(long nowMillis) {
    primed = true;
    state = GolemBrainState.SEEK;
    idleSince = nowMillis;
    moving = false;
  }

  void reset() {
    state = GolemBrainState.IDLE;
    primed = false;
    moving = false;
    leashBroken = false;
    wandered = false;
    teleported = false;
  }

  private void updateLeash(Senses senses, double leashRadius) {
    double leash = switch (senses.mode()) {
      case FOLLOW -> senses.ownerDistance();
      case STAY -> senses.anchorDistance();
      case HOME, WORK -> senses.homeDistance();
    };
    if (leashBroken) {
      leashBroken = leash > leashRadius / 2D;
    } else if (senses.hasTarget() && leash > leashRadius) {
      leashBroken = true;
    }
  }

  private Decision follow(Senses senses, GolemBrainSettings settings) {
    long now = senses.nowMillis();
    double distance = senses.ownerDistance();
    if (Double.isNaN(distance)) {
      return settle(now, GolemBrainState.IDLE, Desire.STAND);
    }
    if (distance > settings.followTeleportDistance() && senses.ownerGrounded() && teleportReady(now)) {
      teleported = true;
      lastTeleportAt = now;
      return settle(now, GolemBrainState.FOLLOW, Desire.TELEPORT);
    }
    double threshold = moving ? FOLLOW_STOP_DISTANCE : FOLLOW_START_DISTANCE;
    return settle(now, GolemBrainState.FOLLOW, distance > threshold ? Desire.OWNER : Desire.STAND);
  }

  private Decision stay(Senses senses) {
    return approach(senses.nowMillis(), GolemBrainState.STAY, senses.anchorDistance(), Desire.ANCHOR, GolemBrainState.STAY);
  }

  private Decision home(Senses senses) {
    return approach(senses.nowMillis(), GolemBrainState.HOME, senses.homeDistance(), Desire.HOME, GolemBrainState.IDLE);
  }

  private Decision approach(long now, GolemBrainState next, double distance, Desire desire, GolemBrainState unreachable) {
    if (Double.isNaN(distance)) {
      return settle(now, unreachable, Desire.STAND);
    }
    double threshold = moving ? ARRIVE_DISTANCE : RESUME_DISTANCE;
    return settle(now, next, distance > threshold ? desire : Desire.STAND);
  }

  private Decision work(Senses senses, GolemBrainSettings settings) {
    long now = senses.nowMillis();
    double distance = senses.homeDistance();
    if (Double.isNaN(distance)) {
      return settle(now, GolemBrainState.IDLE, Desire.STAND);
    }
    if (state == GolemBrainState.RETURN_HOME) {
      return distance > ARRIVE_DISTANCE
          ? settle(now, GolemBrainState.RETURN_HOME, Desire.HOME)
          : settle(now, GolemBrainState.SEEK, Desire.STAND);
    }
    if (distance > NEAR_HOME_DISTANCE) {
      boolean idleTooLong = idle(state) && now - idleSince >= settings.idleReturnMillis();
      return distance > settings.leashRadius() || idleTooLong
          ? settle(now, GolemBrainState.RETURN_HOME, Desire.HOME)
          : settle(now, GolemBrainState.SEEK, Desire.STAND);
    }
    if (!wandered || now - lastWanderAt >= WANDER_INTERVAL_MILLIS) {
      wandered = true;
      lastWanderAt = now;
      return settle(now, GolemBrainState.SEEK, Desire.WANDER);
    }
    return settle(now, GolemBrainState.SEEK, Desire.KEEP);
  }

  private boolean teleportReady(long now) {
    return !teleported || now - lastTeleportAt >= TELEPORT_COOLDOWN_MILLIS;
  }

  private Decision settle(long now, GolemBrainState next, Desire desire) {
    if (next != state && idle(next)) {
      idleSince = now;
    }
    state = next;
    Movement movement = switch (desire) {
      case OWNER -> move(Movement.OWNER);
      case HOME -> move(Movement.HOME);
      case ANCHOR -> move(Movement.ANCHOR);
      case WANDER -> move(Movement.WANDER);
      case TELEPORT -> {
        moving = false;
        yield Movement.TELEPORT;
      }
      case STAND -> stand();
      case KEEP -> Movement.NONE;
    };
    return new Decision(state, movement, leashBroken);
  }

  private static boolean idle(GolemBrainState candidate) {
    return candidate == GolemBrainState.IDLE || candidate == GolemBrainState.SEEK;
  }

  private Movement move(Movement movement) {
    moving = true;
    return movement;
  }

  private Movement stand() {
    if (!moving) {
      return Movement.NONE;
    }
    moving = false;
    return Movement.STOP;
  }

  enum Movement {
    NONE,
    STOP,
    OWNER,
    HOME,
    ANCHOR,
    WANDER,
    TELEPORT
  }

  private enum Desire {
    OWNER,
    HOME,
    ANCHOR,
    WANDER,
    TELEPORT,
    STAND,
    KEEP
  }

  record Senses(long nowMillis, GolemMode mode, double ownerDistance, boolean ownerGrounded, double homeDistance,
                double anchorDistance, boolean hasTarget) {
    Senses {
      Objects.requireNonNull(mode);
    }
  }

  record Decision(GolemBrainState state, Movement movement, boolean dropTarget) {
  }
}
