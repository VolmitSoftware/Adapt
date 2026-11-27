package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.event.block.CrafterCraftEvent;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;

import java.util.List;

import static art.arcane.adapt.bukkit.event.BukkitEventMaps.block;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.entity;
import static art.arcane.adapt.bukkit.event.BukkitTranslations.cancel;

/** Translations of native events every Bukkit server added in 1.21. */
final class Bukkit121Translations {
  private Bukkit121Translations() {
  }

  static List<Translation<?>> all() {
    return List.of(REMOVE, CRAFTER_CRAFT);
  }

  static final Translation<org.bukkit.event.entity.EntityRemoveEvent> REMOVE = BukkitTranslations.simple(
      org.bukkit.event.entity.EntityRemoveEvent.class, EntityRemoveEvent.class, (event, poster) ->
          poster.post(new EntityRemoveEvent(entity(event.getEntity()), removeCause(event.getCause()))));

  static final Translation<org.bukkit.event.block.CrafterCraftEvent> CRAFTER_CRAFT = BukkitTranslations.simple(
      org.bukkit.event.block.CrafterCraftEvent.class, CrafterCraftEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        CrafterCraftEvent core = new CrafterCraftEvent(block(event.getBlock()), BukkitRefs.id(event.getRecipe().getKey()),
            GapEventMaps.itemOrEmpty(event.getResult()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  private static EntityRemoveEvent.Cause removeCause(org.bukkit.event.entity.EntityRemoveEvent.Cause cause) {
    if (cause == null) {
      return EntityRemoveEvent.Cause.UNKNOWN;
    }
    return switch (cause) {
      case DEATH -> EntityRemoveEvent.Cause.DEATH;
      case DESPAWN -> EntityRemoveEvent.Cause.DESPAWN;
      case UNLOAD, PLAYER_QUIT -> EntityRemoveEvent.Cause.UNLOAD;
      case PICKUP -> EntityRemoveEvent.Cause.PICKUP;
      default -> EntityRemoveEvent.Cause.DISCARD;
    };
  }
}
