package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.event.adapt.AdaptBrewCompleteEvent;

import java.util.List;

/** Adapt's own public Bukkit events, posted to the bus at each priority like any native event. */
final class AdaptApiTranslations {
  static final Translation<art.arcane.adapt.api.potion.AdaptBrewCompleteEvent> BREW_COMPLETE =
      BukkitTranslations.simple(art.arcane.adapt.api.potion.AdaptBrewCompleteEvent.class, AdaptBrewCompleteEvent.class,
          (event, poster) -> poster.post(new AdaptBrewCompleteEvent(BukkitRefs.block(event.getBlock()),
              event.getRecipe().getId(), event.getBrewerId(), event.getBrewedPotions())));

  private AdaptApiTranslations() {
  }

  static List<Translation<?>> all() {
    return List.of(BREW_COMPLETE);
  }
}
