package art.arcane.adapt.api.content;

import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;

/** The periodic call a skill registers with {@link SimpleSkill#pulseLearners}. */
@ApiStatus.AvailableSince("1.0.0")
@FunctionalInterface
public interface LearnerPulse {
  /**
   * Runs on the player's thread.
   *
   * @param player        an online player whose data has loaded
   * @param elapsedMillis the time since this player's last pulse of this skill, at most four cadences
   */
  void pulse(PlayerRef player, long elapsedMillis);
}
