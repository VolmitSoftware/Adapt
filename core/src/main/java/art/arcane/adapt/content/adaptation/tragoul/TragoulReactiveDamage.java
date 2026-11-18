package art.arcane.adapt.content.adaptation.tragoul;

final class TragoulReactiveDamage {
  private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();

  private TragoulReactiveDamage() {
  }

  static boolean isActive() {
    return Boolean.TRUE.equals(ACTIVE.get());
  }

  static void apply(Runnable damage) {
    Boolean previous = ACTIVE.get();
    ACTIVE.set(true);
    try {
      damage.run();
    } finally {
      if (previous == null) {
        ACTIVE.remove();
      } else {
        ACTIVE.set(previous);
      }
    }
  }
}
