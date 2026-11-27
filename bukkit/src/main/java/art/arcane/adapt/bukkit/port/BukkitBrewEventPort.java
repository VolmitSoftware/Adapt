package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.api.item.BrewingRecipe;
import art.arcane.adapt.engine.potion.AdaptBrewCompleteEvent;
import art.arcane.adapt.engine.potion.BrewEventPort;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.world.BlockRef;
import org.bukkit.Bukkit;

import java.util.UUID;

/** Fires the public Bukkit brew event; Adapt's own handlers receive it through the event bridge. */
final class BukkitBrewEventPort implements BrewEventPort {
  @Override
  public void brewed(BlockRef block, BrewingRecipe recipe, UUID brewerId, int brewedPotions) {
    Bukkit.getPluginManager().callEvent(new AdaptBrewCompleteEvent(BukkitRefs.block(block), recipe, brewerId, brewedPotions));
  }
}
