package art.arcane.adapt.api.golem;

import java.util.Objects;

public final class GolemWorkXp {
  private static final String KEY_PREFIX = "golemancy:work:";
  private static volatile Sink sink = Sink.NONE;

  private GolemWorkXp() {
  }

  public static void setSink(Sink next) {
    sink = Objects.requireNonNull(next);
  }

  public static void pay(GolemHandle handle, GolemCoreType core, double xp) {
    if (!Double.isFinite(xp) || xp <= 0D) {
      return;
    }
    sink.pay(Objects.requireNonNull(handle), Objects.requireNonNull(core), xp);
  }

  public static String rewardKey(GolemCoreType core) {
    return KEY_PREFIX + core.id();
  }

  @FunctionalInterface
  public interface Sink {
    Sink NONE = (handle, core, xp) -> {
    };

    void pay(GolemHandle handle, GolemCoreType core, double xp);
  }
}
