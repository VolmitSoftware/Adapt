package art.arcane.adapt.api.advancement;

import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * Grants an advancement and skill XP once a player's stat reaches a goal. The stat is a counter the content raises
 * with {@code addStat}.
 *
 * @param advancement the key of an {@link AdaptAdvancement} the same content registered
 * @param stat        the counter watched, such as {@code food.eaten}
 * @param goal        the value that completes it
 * @param reward      XP granted in the owning skill; read when the milestone completes, so it may come from config
 */
@ApiStatus.AvailableSince("1.0.0")
public record Milestone(String advancement, String stat, double goal, DoubleSupplier reward) {
  public Milestone {
    Objects.requireNonNull(advancement, "advancement");
    Objects.requireNonNull(stat, "stat");
    Objects.requireNonNull(reward, "reward");
    if (stat.isBlank() || !Double.isFinite(goal) || goal <= 0D) {
      throw new IllegalArgumentException("A milestone needs a stat and a positive goal: " + advancement);
    }
  }

  /** A milestone with a fixed XP reward. */
  public Milestone(String advancement, String stat, double goal, double reward) {
    this(advancement, stat, goal, () -> reward);
  }
}
