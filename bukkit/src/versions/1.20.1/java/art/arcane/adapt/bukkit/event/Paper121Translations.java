package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.entity.EntityKnockbackEvent;
import art.arcane.adapt.platform.event.entity.EntityPushedByEntityAttackEvent;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.value.Vec3;
import org.bukkit.util.Vector;

import java.util.List;

import static art.arcane.adapt.bukkit.event.BukkitEventMaps.entity;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.vec;
import static art.arcane.adapt.bukkit.event.BukkitTranslations.cancel;

/**
 * Paper 1.20.1 has no removal cause, no general knockback event and no crafter. Removal comes from the remove-from-world
 * event without a cause, and knockback from the attack push event, which carries the attacker.
 */
final class Paper121Translations {
  private Paper121Translations() {
  }

  static List<Translation<?>> all() {
    return List.of(REMOVE, KNOCKBACK);
  }

  static final Translation<com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent> REMOVE = BukkitTranslations.simple(
      com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent.class, EntityRemoveEvent.class, (event, poster) ->
          poster.post(new EntityRemoveEvent(entity(event.getEntity()), EntityRemoveEvent.Cause.UNKNOWN)));

  static final Translation<io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent> KNOCKBACK = new Translation<>(
      io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent.class,
      List.of(EntityKnockbackEvent.class, EntityPushedByEntityAttackEvent.class)) {
    @Override
    public Class<? extends EntityKnockbackEvent> target(io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent event) {
      return event.getPushedBy() == null ? EntityKnockbackEvent.class : EntityPushedByEntityAttackEvent.class;
    }

    @Override
    public void bridge(io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent event, Poster poster) {
      boolean cancelled = event.isCancelled();
      Vector acceleration = event.getAcceleration();
      Vec3 knockback = vec(acceleration);
      EntityRef pushedBy = entity(event.getPushedBy());
      EntityKnockbackEvent core = pushedBy == null
          ? new EntityKnockbackEvent(entity(event.getEntity()), EntityKnockbackEvent.Cause.DAMAGE, knockback)
          : new EntityPushedByEntityAttackEvent(entity(event.getEntity()), EntityKnockbackEvent.Cause.ENTITY_ATTACK, knockback,
              pushedBy);
      core.setCancelled(cancelled);
      poster.post(core);
      cancel(event, cancelled, core);
      if (!core.knockback().equals(knockback)) {
        Vec3 changed = core.knockback();
        acceleration.setX(changed.x()).setY(changed.y()).setZ(changed.z());
      }
    }
  };
}
