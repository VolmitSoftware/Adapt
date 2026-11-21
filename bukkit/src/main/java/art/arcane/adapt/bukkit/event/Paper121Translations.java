package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.event.block.CrafterCraftEvent;
import art.arcane.adapt.platform.event.entity.EntityKnockbackEvent;
import art.arcane.adapt.platform.event.entity.EntityPushedByEntityAttackEvent;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.value.Vec3;

import java.util.List;
import java.util.Map;

import static art.arcane.adapt.bukkit.event.BukkitEventMaps.block;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.entity;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.vec;
import static art.arcane.adapt.bukkit.event.BukkitTranslations.cancel;

/** Translations of Paper-only native events. */
final class Paper121Translations {
  private static final Map<io.papermc.paper.event.entity.EntityKnockbackEvent.Cause, EntityKnockbackEvent.Cause> KNOCKBACK_CAUSES =
      BukkitEventMaps.byName(io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.class, EntityKnockbackEvent.Cause.class,
          EntityKnockbackEvent.Cause.UNKNOWN);

  private Paper121Translations() {
  }

  static List<Translation<?>> all() {
    return List.of(REMOVE, KNOCKBACK, CRAFTER_CRAFT);
  }

  static final Translation<org.bukkit.event.entity.EntityRemoveEvent> REMOVE = BukkitTranslations.simple(
      org.bukkit.event.entity.EntityRemoveEvent.class, EntityRemoveEvent.class, (event, poster) ->
          poster.post(new EntityRemoveEvent(entity(event.getEntity()), removeCause(event.getCause()))));

  static final Translation<io.papermc.paper.event.entity.EntityKnockbackEvent> KNOCKBACK = new Translation<>(
      io.papermc.paper.event.entity.EntityKnockbackEvent.class,
      List.of(EntityKnockbackEvent.class, EntityPushedByEntityAttackEvent.class)) {
    @Override
    public Class<? extends EntityKnockbackEvent> target(io.papermc.paper.event.entity.EntityKnockbackEvent event) {
      return event instanceof io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent
          ? EntityPushedByEntityAttackEvent.class
          : EntityKnockbackEvent.class;
    }

    @Override
    public void bridge(io.papermc.paper.event.entity.EntityKnockbackEvent event, Poster poster) {
      boolean cancelled = event.isCancelled();
      Vec3 knockback = vec(event.getKnockback());
      EntityKnockbackEvent.Cause cause = knockbackCause(event.getCause());
      EntityKnockbackEvent core = event instanceof io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent pushed
          ? new EntityPushedByEntityAttackEvent(entity(event.getEntity()), cause, knockback, entity(pushed.getPushedBy()))
          : new EntityKnockbackEvent(entity(event.getEntity()), cause, knockback);
      core.setCancelled(cancelled);
      poster.post(core);
      cancel(event, cancelled, core);
      if (!core.knockback().equals(knockback)) {
        Vec3 changed = core.knockback();
        event.setKnockback(new org.bukkit.util.Vector(changed.x(), changed.y(), changed.z()));
      }
    }
  };

  static final Translation<org.bukkit.event.block.CrafterCraftEvent> CRAFTER_CRAFT = BukkitTranslations.simple(
      org.bukkit.event.block.CrafterCraftEvent.class, CrafterCraftEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        CrafterCraftEvent core = new CrafterCraftEvent(block(event.getBlock()), BukkitRefs.id(event.getRecipe().getKey()),
            GapEventMaps.itemOrEmpty(event.getResult()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  private static EntityKnockbackEvent.Cause knockbackCause(io.papermc.paper.event.entity.EntityKnockbackEvent.Cause cause) {
    return cause == null ? EntityKnockbackEvent.Cause.UNKNOWN : KNOCKBACK_CAUSES.get(cause);
  }

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
