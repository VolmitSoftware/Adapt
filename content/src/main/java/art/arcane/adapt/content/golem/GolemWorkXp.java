package art.arcane.adapt.content.golem;

import java.util.Objects;

public final class GolemWorkXp {
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

  @FunctionalInterface
  public interface Sink {
    Sink NONE = (handle, core, xp) -> {
    };

    void pay(GolemHandle handle, GolemCoreType core, double xp);
  }
}
