package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.platform.event.entity.EntityKnockbackEvent;
import art.arcane.adapt.platform.event.entity.EntityPushedByEntityAttackEvent;
import art.arcane.adapt.platform.value.Vec3;

import java.util.List;
import java.util.Map;

import static art.arcane.adapt.bukkit.event.BukkitEventMaps.entity;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.vec;
import static art.arcane.adapt.bukkit.event.BukkitTranslations.cancel;

/** Translations of Paper-only native events added in 1.21. */
final class Paper121Translations {
  private static final Map<io.papermc.paper.event.entity.EntityKnockbackEvent.Cause, EntityKnockbackEvent.Cause> KNOCKBACK_CAUSES =
      BukkitEventMaps.byName(io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.class, EntityKnockbackEvent.Cause.class,
          EntityKnockbackEvent.Cause.UNKNOWN);

  private Paper121Translations() {
  }

  static List<Translation<?>> all() {
    return List.of(KNOCKBACK);
  }

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

  private static EntityKnockbackEvent.Cause knockbackCause(io.papermc.paper.event.entity.EntityKnockbackEvent.Cause cause) {
    return cause == null ? EntityKnockbackEvent.Cause.UNKNOWN : KNOCKBACK_CAUSES.get(cause);
  }

}
