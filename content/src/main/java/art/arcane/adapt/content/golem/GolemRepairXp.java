package art.arcane.adapt.content.golem;

import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.Objects;

public final class GolemRepairXp {
  private static volatile Sink sink = Sink.NONE;

  private GolemRepairXp() {
  }

  public static void setSink(Sink next) {
    sink = Objects.requireNonNull(next);
  }

  public static void repaired(PlayerRef healer, LivingRef golem, double healed) {
    if (!Double.isFinite(healed) || healed <= 0D) {
      return;
    }
    sink.repaired(Objects.requireNonNull(healer), Objects.requireNonNull(golem), healed);
  }

  @FunctionalInterface
  public interface Sink {
    Sink NONE = (healer, golem, healed) -> {
    };

    void repaired(PlayerRef healer, LivingRef golem, double healed);
  }
}
