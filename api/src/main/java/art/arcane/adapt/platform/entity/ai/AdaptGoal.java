package art.arcane.adapt.platform.entity.ai;

import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Set;

/**
 * A custom mob goal, adapted to a Paper goal or a vanilla goal by the platform. All callbacks run
 * on the mob's owning thread during the mob's AI tick and must not block.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface AdaptGoal {
  /** Stable key, used to find and remove the goal later (for example {@code adapt:golem_work}). */
  Id key();

  /** Controls the goal claims while it runs; goals that share a flag do not run together. */
  Set<GoalFlag> flags();

  /** True when the goal should start; checked while it is inactive. */
  boolean shouldActivate();

  /** True while the started goal should keep running; defaults to {@link #shouldActivate()}. */
  default boolean shouldStayActive() {
    return shouldActivate();
  }

  /** Runs once when the goal starts. */
  default void start() {
  }

  /** Runs on each AI tick while the goal is active. */
  default void tick() {
  }

  /** Runs once when the goal stops or is replaced by a higher-priority goal. */
  default void stop() {
  }

  /** True to tick every server tick instead of every other tick, as vanilla allows. */
  default boolean tickEveryTick() {
    return false;
  }
}
