package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.platform.event.entity.EndermanAttackPlayerEvent;

import java.util.List;

import static art.arcane.adapt.bukkit.event.BukkitEventMaps.entity;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.player;
import static art.arcane.adapt.bukkit.event.BukkitTranslations.cancel;

final class EndermanTranslations {
  private EndermanTranslations() {
  }

  static List<Translation<?>> all() {
    return List.of(ATTACK_PLAYER);
  }

  static final Translation<com.destroystokyo.paper.event.entity.EndermanAttackPlayerEvent> ATTACK_PLAYER =
      BukkitTranslations.simple(com.destroystokyo.paper.event.entity.EndermanAttackPlayerEvent.class,
          EndermanAttackPlayerEvent.class, (event, poster) -> {
            boolean cancelled = event.isCancelled();
            EndermanAttackPlayerEvent core = new EndermanAttackPlayerEvent(entity(event.getEntity()),
                player(event.getPlayer()), !cancelled);
            poster.post(core);
            cancel(event, cancelled, core);
          });
}
