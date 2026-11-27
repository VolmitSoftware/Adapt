package art.arcane.adapt.platform.event.adapt;

import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;
import java.util.UUID;

/**
 * An Adapt custom brewing recipe finished. brewerId() is the stand owner, or null. Read-only.
 */
@ApiStatus.AvailableSince("1.0.0")
public class AdaptBrewCompleteEvent extends AdaptEvent {
  private final BlockRef block;
  private final String recipeId;
  private final UUID brewerId;
  private final int brewedPotions;

  public AdaptBrewCompleteEvent(BlockRef block, String recipeId, UUID brewerId, int brewedPotions) {
    this.block = Objects.requireNonNull(block, "block");
    this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
    this.brewerId = brewerId;
    this.brewedPotions = brewedPotions;
  }

  /** The brewing stand. */
  public BlockRef block() {
    return block;
  }

  /** Id of the Adapt brewing recipe that finished. */
  public String recipeId() {
    return recipeId;
  }

  /** UUID of the stand's owner, or null. */
  public UUID brewerId() {
    return brewerId;
  }

  /** Number of bottles brewed. */
  public int brewedPotions() {
    return brewedPotions;
  }
}
