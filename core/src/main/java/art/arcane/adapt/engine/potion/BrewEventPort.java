package art.arcane.adapt.engine.potion;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.item.BrewingRecipe;
import art.arcane.adapt.platform.event.adapt.AdaptBrewCompleteEvent;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.UUID;

/**
 * Announces a finished Adapt brew. The Bukkit plugin fires its public brew event, which reaches Adapt's own handlers
 * through the event bridge; without a port the core event goes straight to the bus.
 */
public interface BrewEventPort {
  BrewEventPort BUS = (block, recipe, brewerId, brewedPotions) ->
      AdaptCore.get().getBus().post(new AdaptBrewCompleteEvent(block, recipe.id().toString(), brewerId, brewedPotions));

  void brewed(BlockRef block, BrewingRecipe recipe, UUID brewerId, int brewedPotions);
}
