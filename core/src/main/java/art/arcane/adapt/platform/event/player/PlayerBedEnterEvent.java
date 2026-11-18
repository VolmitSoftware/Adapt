package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.Objects;

/** A player tries to sleep in a bed. Mutable: cancelled. */
public class PlayerBedEnterEvent extends PlayerEvent {
  private final BlockRef bed;
  private final Result result;

  public PlayerBedEnterEvent(PlayerRef player, BlockRef bed, Result result) {
    super(player);
    this.bed = Objects.requireNonNull(bed, "bed");
    this.result = result == null ? Result.OTHER_PROBLEM : result;
  }

  public BlockRef bed() {
    return bed;
  }

  public Result result() {
    return result;
  }

  public enum Result {
    OK,
    NOT_POSSIBLE_HERE,
    NOT_POSSIBLE_NOW,
    TOO_FAR_AWAY,
    NOT_SAFE,
    OBSTRUCTED,
    OTHER_PROBLEM
  }
}
