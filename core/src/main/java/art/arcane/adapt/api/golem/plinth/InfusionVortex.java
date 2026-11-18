package art.arcane.adapt.api.golem.plinth;

final class InfusionVortex {
  static final int ARMS = 4;
  static final double LIFT_END = 0.25D;
  static final double SWIRL_END = 0.85D;
  static final double PEDESTAL_RADIUS = 2D;
  static final double CORE_RADIUS = 0.3D;
  static final double ITEM_HEIGHT = 0.85D;
  static final double PLINTH_HEIGHT = 0.55D;
  static final float MAX_LIFT = 0.6F;
  static final float BOB = 0.06F;
  static final float MAX_SPIN = 30F;
  static final int STREAM_CAP = 6;
  static final int CORE_CAP = 8;
  static final int FRAME_CAP = ARMS * STREAM_CAP + CORE_CAP;
  static final int BURST_CAP = 60;
  static final int RING_POINTS = 32;
  static final int RING_TICKS = 10;
  static final int FIZZLE_TICKS = 10;
  private static final float CENTER_LIFT = 0.3F;
  static final double CORE_HEIGHT = ITEM_HEIGHT + CENTER_LIFT;
  private static final double PEAK_HEIGHT = 1.75D;
  private static final double START_SPEED = 0.045D;
  private static final double END_SPEED = 0.5D;
  private static final double BOB_RADIANS_PER_TICK = Math.PI / 20D;
  private static final double QUARTER_TURN = Math.PI / 2D;
  private static final double TIGHTEN_EXPONENT = 1.5D;
  private static final double LIFT_SPIN_SHARE = 0.15D;
  private static final int LIFT_POINTS = 4;
  private static final int SWIRL_POINTS = 5;
  private static final int MIN_CORE = 2;
  private static final float SWELL_LOW_PITCH = 0.6F;
  private static final float SWELL_PITCH_RISE = 1.3F;
  private static final float SWELL_LOW_VOLUME = 0.25F;
  private static final float SWELL_VOLUME_RISE = 0.5F;
  private static final double RING_START = 0.4D;
  private static final double RING_END = 3.6D;
  private static final double SCATTER_DISTANCE = 1.6D;
  private static final int FIZZLE_START_POINTS = 3;

  private InfusionVortex() {
  }

  static Phase phase(double progress) {
    if (progress < LIFT_END) {
      return Phase.LIFT;
    }
    return progress < SWIRL_END ? Phase.SWIRL : Phase.COLLIDE;
  }

  static double progress(double ticks, int durationTicks) {
    return clamp(ticks / durationTicks);
  }

  static double radius(double progress) {
    return switch (phase(progress)) {
      case LIFT -> PEDESTAL_RADIUS;
      case SWIRL -> CORE_RADIUS + ((PEDESTAL_RADIUS - CORE_RADIUS) * Math.pow(1D - swirl(progress), TIGHTEN_EXPONENT));
      case COLLIDE -> {
        double closing = collide(progress);
        yield CORE_RADIUS * (1D - (closing * closing));
      }
    };
  }

  static double angle(double ticks, int durationTicks) {
    double progress = progress(ticks, durationTicks);
    double squared = progress * progress;
    return durationTicks * ((START_SPEED * progress) + ((END_SPEED - START_SPEED) * squared * squared * progress / 5D));
  }

  static double height(double progress) {
    return switch (phase(progress)) {
      case LIFT -> ITEM_HEIGHT + ((PEAK_HEIGHT - ITEM_HEIGHT) * smooth(clamp(progress) / LIFT_END));
      case SWIRL -> PEAK_HEIGHT + ((CORE_HEIGHT - PEAK_HEIGHT) * smooth(swirl(progress)));
      case COLLIDE -> CORE_HEIGHT;
    };
  }

  static float pedestalLift(double progress, int elapsed, int pedestal) {
    double risen = smooth(clamp(progress) / LIFT_END);
    double bob = Math.sin((elapsed * BOB_RADIANS_PER_TICK) + (pedestal * QUARTER_TURN));
    return (float) ((MAX_LIFT * risen) + (BOB * risen * bob));
  }

  static float centerLift(double progress) {
    return CENTER_LIFT * (float) smooth(clamp(progress) / LIFT_END);
  }

  static float centerSpin(double progress) {
    double tightness = 1D - (radius(progress) / PEDESTAL_RADIUS);
    double share = Math.min(1D, (LIFT_SPIN_SHARE * smooth(clamp(progress) / LIFT_END)) + ((1D - LIFT_SPIN_SHARE) * tightness));
    return PlinthRenderer.IDLE_DEGREES_PER_TICK + ((MAX_SPIN - PlinthRenderer.IDLE_DEGREES_PER_TICK) * (float) share);
  }

  static int streamPoints(Phase phase) {
    return phase == Phase.LIFT ? LIFT_POINTS : SWIRL_POINTS;
  }

  static boolean accentDue(Phase phase, int elapsed) {
    return switch (phase) {
      case LIFT, SWIRL -> elapsed % 6 == 0;
      case COLLIDE -> elapsed % 4 == 0;
    };
  }

  static int coreParticles(double progress) {
    if (phase(progress) != Phase.COLLIDE) {
      return 0;
    }
    return MIN_CORE + (int) Math.round((CORE_CAP - MIN_CORE) * collide(progress));
  }

  static float swellPitch(double progress) {
    return SWELL_LOW_PITCH + (SWELL_PITCH_RISE * (float) swirl(progress));
  }

  static float swellVolume(double progress) {
    return SWELL_LOW_VOLUME + (SWELL_VOLUME_RISE * (float) swirl(progress));
  }

  static double ringRadius(double progress) {
    return RING_START + ((RING_END - RING_START) * easeOut(progress));
  }

  static double scatterRadius(double start, double progress) {
    return start + (SCATTER_DISTANCE * easeOut(progress));
  }

  static int fizzlePoints(int tick) {
    return Math.max(1, FIZZLE_START_POINTS - ((tick * FIZZLE_START_POINTS) / FIZZLE_TICKS));
  }

  private static double swirl(double progress) {
    return clamp((progress - LIFT_END) / (SWIRL_END - LIFT_END));
  }

  private static double collide(double progress) {
    return clamp((progress - SWIRL_END) / (1D - SWIRL_END));
  }

  private static double smooth(double value) {
    double t = clamp(value);
    return t * t * (3D - (2D * t));
  }

  private static double easeOut(double value) {
    double t = clamp(value);
    return 1D - ((1D - t) * (1D - t));
  }

  private static double clamp(double value) {
    return Math.max(0D, Math.min(1D, value));
  }

  enum Phase {
    LIFT,
    SWIRL,
    COLLIDE
  }
}
