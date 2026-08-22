package art.arcane.adapt.content.adaptation.kinetics;

final class KineticsJumpPhysics {
  static final double VANILLA_JUMP_STRENGTH = 0.42D;
  static final double VANILLA_JUMP_HEIGHT = heightForStrength(VANILLA_JUMP_STRENGTH);

  private static final double GRAVITY_PER_TICK = 0.08D;
  private static final double VERTICAL_DRAG = 0.98D;
  private static final double MAX_JUMP_STRENGTH = 4D;
  private static final int MAX_ASCENT_TICKS = 256;
  private static final int SEARCH_ITERATIONS = 80;

  private KineticsJumpPhysics() {
  }

  static double heightForStrength(double jumpStrength) {
    if (!Double.isFinite(jumpStrength) || jumpStrength <= 0D) {
      return 0D;
    }

    double height = 0D;
    double verticalVelocity = Math.min(jumpStrength, MAX_JUMP_STRENGTH);
    for (int tick = 0; tick < MAX_ASCENT_TICKS && verticalVelocity > 0D; tick++) {
      height += verticalVelocity;
      verticalVelocity = (verticalVelocity - GRAVITY_PER_TICK) * VERTICAL_DRAG;
    }
    return height;
  }

  static double strengthForHeight(double targetHeight) {
    if (!Double.isFinite(targetHeight) || targetHeight <= 0D) {
      return 0D;
    }

    double low = 0D;
    double high = VANILLA_JUMP_STRENGTH;
    while (high < MAX_JUMP_STRENGTH && heightForStrength(high) < targetHeight) {
      high = Math.min(MAX_JUMP_STRENGTH, high * 2D);
    }

    if (heightForStrength(high) < targetHeight) {
      return high;
    }

    for (int iteration = 0; iteration < SEARCH_ITERATIONS; iteration++) {
      double midpoint = (low + high) * 0.5D;
      if (heightForStrength(midpoint) < targetHeight) {
        low = midpoint;
      } else {
        high = midpoint;
      }
    }
    return high;
  }

  static double bonusForHeight(double targetHeight) {
    return Math.max(0D, strengthForHeight(targetHeight) - VANILLA_JUMP_STRENGTH);
  }
}
