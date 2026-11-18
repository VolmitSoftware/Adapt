package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.platform.event.entity.EntityDismountEvent;
import art.arcane.adapt.platform.event.entity.EntityMountEvent;

import java.util.List;

import static art.arcane.adapt.bukkit.event.BukkitEventMaps.entity;
import static art.arcane.adapt.bukkit.event.BukkitTranslations.cancel;

final class MountTranslations {
  private MountTranslations() {
  }

  static List<Translation<?>> all() {
    return List.of(MOUNT, DISMOUNT);
  }

  static final Translation<org.bukkit.event.entity.EntityMountEvent> MOUNT = BukkitTranslations.simple(
      org.bukkit.event.entity.EntityMountEvent.class, EntityMountEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        EntityMountEvent core = new EntityMountEvent(entity(event.getEntity()), entity(event.getMount()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.entity.EntityDismountEvent> DISMOUNT = BukkitTranslations.simple(
      org.bukkit.event.entity.EntityDismountEvent.class, EntityDismountEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        EntityDismountEvent core = new EntityDismountEvent(entity(event.getEntity()), entity(event.getDismounted()),
            event.isCancellable());
        core.setCancelled(cancelled);
        poster.post(core);
        if (event.isCancellable()) {
          cancel(event, cancelled, core);
        }
      });
}
