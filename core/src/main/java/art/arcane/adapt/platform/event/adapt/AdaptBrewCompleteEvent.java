package art.arcane.adapt.platform.event.adapt;

import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.Objects;
import java.util.UUID;

/**
 * An Adapt custom brewing recipe finished. brewerId() is the stand owner, or null. Read-only.
 */
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

  public BlockRef block() {
    return block;
  }

  public String recipeId() {
    return recipeId;
  }

  public UUID brewerId() {
    return brewerId;
  }

  public int brewedPotions() {
    return brewedPotions;
  }
}
