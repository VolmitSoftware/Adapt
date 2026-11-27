package art.arcane.adapt.api.player;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A player's vanilla experience as points, the currency enchanting and mending spend, rather than as level and bar
 * progress. The platform's own experience hooks see every change. Needs a running core.
 */
@ApiStatus.Experimental
public final class Experience {
  private Experience() {
  }

  /** The experience points the player's level and bar progress are worth. */
  public static int points(PlayerRef player) {
    return Engine.bridge().abilities().experiencePoints(Objects.requireNonNull(player, "player"));
  }

  /**
   * Sets level and bar progress to what {@code points} are worth, leaving the score counter alone. A negative value
   * counts as zero.
   */
  public static void setPoints(PlayerRef player, int points) {
    Engine.bridge().abilities().setExperiencePoints(Objects.requireNonNull(player, "player"), Math.max(0, points));
  }

  /**
   * Gives {@code points} of vanilla experience through the platform's experience-change hook, so plugins that adjust
   * experience gains see it; returns the points given. Zero or a negative value gives nothing.
   */
  public static int award(PlayerRef player, int points) {
    return points <= 0 ? 0 : Engine.bridge().abilities().awardExperience(Objects.requireNonNull(player, "player"), points);
  }
}
