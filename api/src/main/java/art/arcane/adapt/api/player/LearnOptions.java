package art.arcane.adapt.api.player;

import org.jetbrains.annotations.ApiStatus;

/** How a learn or unlearn is carried out. Start from {@link #paying()} or {@link #forced()}. */
@ApiStatus.AvailableSince("1.0.0")
public final class LearnOptions {
  private static final LearnOptions PAYING = new LearnOptions(false, false);
  private static final LearnOptions FORCED = new LearnOptions(true, false);

  private final boolean force;
  private final boolean quiet;

  private LearnOptions(boolean force, boolean quiet) {
    this.force = force;
    this.quiet = quiet;
  }

  /** What a player clicking the menu gets: knowledge, power and the economy are charged and every rule applies. */
  public static LearnOptions paying() {
    return PAYING;
  }

  /** What an admin command does: free, with no power limit and no permanence; requirements still apply. */
  public static LearnOptions forced() {
    return FORCED;
  }

  /** A copy of these options that gives the player no sound, toast or message. */
  public LearnOptions quietly() {
    return new LearnOptions(force, true);
  }

  /**
   * True when nothing is charged or refunded, and permanence and the power limit are not checked; requirements still
   * are, so a forced learn of an adaptation whose required adaptations are missing fails with
   * {@link LearnResult.Status#REQUIREMENTS_MISSING}.
   */
  public boolean force() {
    return force;
  }

  /** True when the player gets no sound, toast or message. */
  public boolean quiet() {
    return quiet;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof LearnOptions options && force == options.force && quiet == options.quiet;
  }

  @Override
  public int hashCode() {
    return (force ? 2 : 0) + (quiet ? 1 : 0);
  }

  @Override
  public String toString() {
    return "LearnOptions[force=" + force + ", quiet=" + quiet + "]";
  }
}
