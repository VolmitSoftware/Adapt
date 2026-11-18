package art.arcane.adapt.platform.entity.ai;

import art.arcane.adapt.platform.value.Id;

import java.util.Set;

/**
 * A custom mob goal, adapted to a Paper goal or a vanilla goal by the platform. All callbacks run
 * on the mob's owning thread during the mob's AI tick and must not block.
 */
public interface AdaptGoal {
  /** Stable key, used to find and remove the goal later (for example {@code adapt:golem_work}). */
  Id key();

  Set<GoalFlag> flags();

  boolean shouldActivate();

  default boolean shouldStayActive() {
    return shouldActivate();
  }

  default void start() {
  }

  default void tick() {
  }

  default void stop() {
  }

  /** True to tick every server tick instead of every other tick, as vanilla allows. */
  default boolean tickEveryTick() {
    return false;
  }
}
