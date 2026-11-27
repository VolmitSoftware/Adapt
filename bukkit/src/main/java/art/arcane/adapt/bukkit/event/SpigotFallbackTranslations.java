package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.event.entity.EntityKnockbackEvent;
import art.arcane.adapt.platform.event.entity.EntityPushedByEntityAttackEvent;
import art.arcane.adapt.platform.event.entity.EntityWorldEvent;
import art.arcane.adapt.platform.event.inventory.ItemCraftedEvent;
import art.arcane.adapt.platform.event.player.PlayerJumpEvent;
import art.arcane.adapt.platform.value.Vec3;
import org.bukkit.Statistic;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

import static art.arcane.adapt.bukkit.event.BukkitEventMaps.entity;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.player;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.vec;
import static art.arcane.adapt.bukkit.event.BukkitTranslations.cancel;

/**
 * Stand-ins for Paper-only events, built from the nearest Bukkit event. Loaded only on servers without Paper's API, where
 * they post the same core events with less detail: jumps cannot be cancelled, crafts report one result per click, and
 * entities count as added when they spawn or their chunk loads.
 */
@SuppressWarnings("removal")
final class SpigotFallbackTranslations {
  private static final Map<org.bukkit.event.entity.EntityKnockbackEvent.KnockbackCause, EntityKnockbackEvent.Cause> KNOCKBACK_CAUSES =
      BukkitEventMaps.byName(org.bukkit.event.entity.EntityKnockbackEvent.KnockbackCause.class, EntityKnockbackEvent.Cause.class,
          EntityKnockbackEvent.Cause.UNKNOWN);

  private SpigotFallbackTranslations() {
  }

  static List<Translation<?>> all() {
    return List.of(KNOCKBACK, JUMP, ADD_ON_SPAWN, ADD_ON_LOAD, REMOVE_FROM_WORLD, CRAFTED);
  }

  static final Translation<org.bukkit.event.entity.EntityKnockbackEvent> KNOCKBACK = new Translation<>(
      org.bukkit.event.entity.EntityKnockbackEvent.class,
      List.of(EntityKnockbackEvent.class, EntityPushedByEntityAttackEvent.class)) {
    @Override
    public Class<? extends EntityKnockbackEvent> target(org.bukkit.event.entity.EntityKnockbackEvent event) {
      return event instanceof org.bukkit.event.entity.EntityKnockbackByEntityEvent
          ? EntityPushedByEntityAttackEvent.class
          : EntityKnockbackEvent.class;
    }

    @Override
    public void bridge(org.bukkit.event.entity.EntityKnockbackEvent event, Poster poster) {
      boolean cancelled = event.isCancelled();
      Vec3 knockback = vec(event.getFinalKnockback());
      EntityKnockbackEvent.Cause cause = event.getCause() == null
          ? EntityKnockbackEvent.Cause.UNKNOWN
          : KNOCKBACK_CAUSES.get(event.getCause());
      EntityKnockbackEvent core = event instanceof org.bukkit.event.entity.EntityKnockbackByEntityEvent byEntity
          ? new EntityPushedByEntityAttackEvent(entity(event.getEntity()), cause, knockback, entity(byEntity.getSourceEntity()))
          : new EntityKnockbackEvent(entity(event.getEntity()), cause, knockback);
      core.setCancelled(cancelled);
      poster.post(core);
      cancel(event, cancelled, core);
      if (!core.knockback().equals(knockback)) {
        Vec3 changed = core.knockback();
        event.setFinalKnockback(new org.bukkit.util.Vector(changed.x(), changed.y(), changed.z()));
      }
    }
  };

  static final Translation<org.bukkit.event.player.PlayerStatisticIncrementEvent> JUMP = new Translation<>(
      org.bukkit.event.player.PlayerStatisticIncrementEvent.class, List.of(PlayerJumpEvent.class)) {
    @Override
    public Class<? extends PlayerJumpEvent> target(org.bukkit.event.player.PlayerStatisticIncrementEvent event) {
      return event.getStatistic() == Statistic.JUMP ? PlayerJumpEvent.class : null;
    }

    @Override
    public boolean cancelled(org.bukkit.event.player.PlayerStatisticIncrementEvent event) {
      return false;
    }

    @Override
    public void bridge(org.bukkit.event.player.PlayerStatisticIncrementEvent event, Poster poster) {
      Player jumper = event.getPlayer();
      poster.post(new PlayerJumpEvent(player(jumper), BukkitRefs.loc(jumper.getLocation())));
    }
  };

  static final Translation<org.bukkit.event.entity.EntitySpawnEvent> ADD_ON_SPAWN = new Translation<>(
      org.bukkit.event.entity.EntitySpawnEvent.class, List.of(EntityWorldEvent.class)) {
    @Override
    public boolean cancelled(org.bukkit.event.entity.EntitySpawnEvent event) {
      return false;
    }

    @Override
    public void bridge(org.bukkit.event.entity.EntitySpawnEvent event, Poster poster) {
      if (!event.isCancelled()) {
        poster.post(new EntityWorldEvent(entity(event.getEntity()), EntityWorldEvent.Phase.ADD));
      }
    }
  };

  static final Translation<org.bukkit.event.world.EntitiesLoadEvent> ADD_ON_LOAD = BukkitTranslations.simple(
      org.bukkit.event.world.EntitiesLoadEvent.class, EntityWorldEvent.class, (event, poster) -> {
        for (Entity loaded : event.getEntities()) {
          poster.post(new EntityWorldEvent(entity(loaded), EntityWorldEvent.Phase.ADD));
        }
      });

  static final Translation<org.bukkit.event.entity.EntityRemoveEvent> REMOVE_FROM_WORLD = BukkitTranslations.simple(
      org.bukkit.event.entity.EntityRemoveEvent.class, EntityWorldEvent.class,
      (event, poster) -> poster.post(new EntityWorldEvent(entity(event.getEntity()), EntityWorldEvent.Phase.REMOVE)));

  static final Translation<org.bukkit.event.inventory.CraftItemEvent> CRAFTED = new Translation<>(
      org.bukkit.event.inventory.CraftItemEvent.class, List.of(ItemCraftedEvent.class)) {
    @Override
    public Class<? extends ItemCraftedEvent> target(org.bukkit.event.inventory.CraftItemEvent event) {
      return event.getWhoClicked() instanceof Player ? ItemCraftedEvent.class : null;
    }

    @Override
    public boolean cancelled(org.bukkit.event.inventory.CraftItemEvent event) {
      return false;
    }

    @Override
    public void bridge(org.bukkit.event.inventory.CraftItemEvent event, Poster poster) {
      if (event.isCancelled() || !(event.getWhoClicked() instanceof Player crafter)) {
        return;
      }
      poster.post(new ItemCraftedEvent(player(crafter), GapEventMaps.itemOrEmpty(event.getInventory().getResult()),
          GapEventMaps.key(event.getRecipe())));
    }
  };
}
