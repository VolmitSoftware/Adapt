package art.arcane.adapt.api.golem;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Objects;

public final class GolemRepairXp {
  public static final String REWARD_KEY = "golemancy:repair";

  private static volatile Sink sink = Sink.NONE;

  private GolemRepairXp() {
  }

  public static void setSink(Sink next) {
    sink = Objects.requireNonNull(next);
  }

  public static void repaired(Player healer, LivingEntity golem, double healed) {
    if (!Double.isFinite(healed) || healed <= 0D) {
      return;
    }
    sink.repaired(Objects.requireNonNull(healer), Objects.requireNonNull(golem), healed);
  }

  @FunctionalInterface
  public interface Sink {
    Sink NONE = (healer, golem, healed) -> {
    };

    void repaired(Player healer, LivingEntity golem, double healed);
  }
}
