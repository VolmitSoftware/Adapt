package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A player tries to sleep in a bed. {@link #result()} is vanilla's verdict; cancelling an {@link Result#OK}
 * attempt keeps the player awake. Mod platforms post a refused attempt after vanilla refused it. Mutable:
 * cancelled.
 */
@ApiStatus.AvailableSince("1.0.0")
public class PlayerBedEnterEvent extends PlayerEvent {
  private final BlockRef bed;
  private final Result result;

  public PlayerBedEnterEvent(PlayerRef player, BlockRef bed, Result result) {
    super(player);
    this.bed = Objects.requireNonNull(bed, "bed");
    this.result = result == null ? Result.OTHER_PROBLEM : result;
  }

  /** The bed block the player used. */
  public BlockRef bed() {
    return bed;
  }

  /** Whether vanilla lets the player sleep, and why not. */
  public Result result() {
    return result;
  }

  /**
   * Outcome of a bed attempt, matching Bukkit's bed enter results.
   * A {@code switch} over this enum needs a default branch: minor releases add constants.
   */
  public enum Result {
    /** The player can sleep. */
    OK,
    /** Beds do not work in this dimension. */
    NOT_POSSIBLE_HERE,
    /** Sleeping is not possible at this time of day. */
    NOT_POSSIBLE_NOW,
    TOO_FAR_AWAY,
    /** Monsters are nearby. */
    NOT_SAFE,
    /** The bed is blocked. */
    OBSTRUCTED,
    /** Any other refusal, also used when the platform gave no result. */
    OTHER_PROBLEM
  }
}
