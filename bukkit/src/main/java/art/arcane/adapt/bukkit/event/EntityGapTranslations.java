package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.entity.AreaEffectCloudApplyEvent;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.EntityAttemptSmashAttackEvent;
import art.arcane.adapt.platform.event.entity.EntityEquipmentChangedEvent;
import art.arcane.adapt.platform.event.entity.EntityInsideBlockEvent;
import art.arcane.adapt.platform.event.entity.EntityKnockbackEvent;
import art.arcane.adapt.platform.event.entity.EntityLungeEvent;
import art.arcane.adapt.platform.event.entity.EntityPathfindEvent;
import art.arcane.adapt.platform.event.entity.EntityPushedByEntityAttackEvent;
import art.arcane.adapt.platform.event.entity.EntityToggleGlideEvent;
import art.arcane.adapt.platform.event.entity.EntityWorldEvent;
import art.arcane.adapt.platform.event.entity.ItemDespawnEvent;
import art.arcane.adapt.platform.event.entity.ItemMergeEvent;
import art.arcane.adapt.platform.event.entity.ItemSpawnEvent;
import art.arcane.adapt.platform.event.entity.ItemTransportingEntityValidateTargetEvent;
import art.arcane.adapt.platform.event.entity.LingeringPotionSplashEvent;
import art.arcane.adapt.platform.event.entity.PiglinBarterEvent;
import art.arcane.adapt.platform.event.entity.PotionSplashEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Vec3;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent.EquipmentChange;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static art.arcane.adapt.bukkit.event.BukkitEventMaps.block;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.entity;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.living;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.stack;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.vec;
import static art.arcane.adapt.bukkit.event.BukkitTranslations.cancel;

final class EntityGapTranslations {
  private EntityGapTranslations() {
  }

  static List<Translation<?>> all() {
    return List.of(ADD_TO_WORLD, REMOVE_FROM_WORLD, SMASH_ATTEMPT, SPAWN, ITEM_MERGE, ITEM_DESPAWN, LUNGE, POTION_SPLASH,
        LINGERING_SPLASH, CLOUD_APPLY, KNOCKBACK, INSIDE_BLOCK, TOGGLE_GLIDE, PIGLIN_BARTER, EQUIPMENT_CHANGED,
        TRANSPORT_TARGET, PATHFIND);
  }

  static final Translation<com.destroystokyo.paper.event.entity.EntityAddToWorldEvent> ADD_TO_WORLD = BukkitTranslations.simple(
      com.destroystokyo.paper.event.entity.EntityAddToWorldEvent.class, EntityWorldEvent.class,
      (event, poster) -> poster.post(new EntityWorldEvent(entity(event.getEntity()), EntityWorldEvent.Phase.ADD)));

  static final Translation<com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent> REMOVE_FROM_WORLD =
      BukkitTranslations.simple(com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent.class, EntityWorldEvent.class,
          (event, poster) -> poster.post(new EntityWorldEvent(entity(event.getEntity()), EntityWorldEvent.Phase.REMOVE)));

  static final Translation<io.papermc.paper.event.entity.EntityAttemptSmashAttackEvent> SMASH_ATTEMPT = BukkitTranslations.simple(
      io.papermc.paper.event.entity.EntityAttemptSmashAttackEvent.class, EntityAttemptSmashAttackEvent.class, (event, poster) -> {
        UseResult result = BukkitEventMaps.result(event.getResult());
        EntityAttemptSmashAttackEvent core = new EntityAttemptSmashAttackEvent(living((LivingEntity) event.getEntity()),
            living(event.getTarget()), GapEventMaps.itemOrEmpty(event.getWeapon()), event.getOriginalResult(), result);
        poster.post(core);
        if (core.result() != result) {
          event.setResult(BukkitEventMaps.result(core.result()));
        }
      });

  static final Translation<org.bukkit.event.entity.EntitySpawnEvent> SPAWN = new Translation<>(
      org.bukkit.event.entity.EntitySpawnEvent.class, List.of(CreatureSpawnEvent.class, ItemSpawnEvent.class)) {
    @Override
    public Class<? extends AdaptEvent> target(org.bukkit.event.entity.EntitySpawnEvent event) {
      if (event instanceof org.bukkit.event.entity.CreatureSpawnEvent) {
        return CreatureSpawnEvent.class;
      }
      return event instanceof org.bukkit.event.entity.ItemSpawnEvent ? ItemSpawnEvent.class : null;
    }

    @Override
    public void bridge(org.bukkit.event.entity.EntitySpawnEvent event, Poster poster) {
      boolean cancelled = event.isCancelled();
      art.arcane.adapt.platform.event.CancellableEvent core;
      if (event instanceof org.bukkit.event.entity.CreatureSpawnEvent creature) {
        core = new CreatureSpawnEvent(living(creature.getEntity()), BukkitRefs.loc(creature.getLocation()),
            GapEventMaps.spawnReason(creature.getSpawnReason()));
      } else if (event instanceof org.bukkit.event.entity.ItemSpawnEvent item) {
        core = new ItemSpawnEvent(BukkitRefs.itemEntity(item.getEntity()), BukkitRefs.loc(item.getLocation()));
      } else {
        return;
      }
      core.setCancelled(cancelled);
      poster.post(core);
      cancel(event, cancelled, core);
    }
  };

  static final Translation<org.bukkit.event.entity.ItemMergeEvent> ITEM_MERGE = BukkitTranslations.simple(
      org.bukkit.event.entity.ItemMergeEvent.class, ItemMergeEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        ItemMergeEvent core = new ItemMergeEvent(BukkitRefs.itemEntity(event.getEntity()), BukkitRefs.itemEntity(event.getTarget()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.entity.ItemDespawnEvent> ITEM_DESPAWN = BukkitTranslations.simple(
      org.bukkit.event.entity.ItemDespawnEvent.class, ItemDespawnEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        ItemDespawnEvent core = new ItemDespawnEvent(BukkitRefs.itemEntity(event.getEntity()), BukkitRefs.loc(event.getLocation()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<io.papermc.paper.event.entity.EntityLungeEvent> LUNGE = BukkitTranslations.simple(
      io.papermc.paper.event.entity.EntityLungeEvent.class, EntityLungeEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        int power = event.getLungePower();
        EntityLungeEvent core = new EntityLungeEvent(living((LivingEntity) event.getEntity()), power);
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
        if (core.power() != power) {
          event.setLungePower(core.power());
        }
      });

  static final Translation<org.bukkit.event.entity.PotionSplashEvent> POTION_SPLASH = BukkitTranslations.simple(
      org.bukkit.event.entity.PotionSplashEvent.class, PotionSplashEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        ThrownPotion potion = event.getPotion();
        Map<LivingRef, Double> affected = new LinkedHashMap<>();
        for (LivingEntity entity : event.getAffectedEntities()) {
          affected.put(living(entity), event.getIntensity(entity));
        }
        PotionSplashEvent core = new PotionSplashEvent(BukkitRefs.projectile(potion), GapEventMaps.itemOrEmpty(potion.getItem()),
            GapEventMaps.effects(potion.getEffects()), affected);
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.entity.LingeringPotionSplashEvent> LINGERING_SPLASH = BukkitTranslations.simple(
      org.bukkit.event.entity.LingeringPotionSplashEvent.class, LingeringPotionSplashEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        ThrownPotion potion = event.getEntity();
        LingeringPotionSplashEvent core = new LingeringPotionSplashEvent(BukkitRefs.projectile(potion),
            GapEventMaps.itemOrEmpty(potion.getItem()), entity(event.getAreaEffectCloud()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.entity.AreaEffectCloudApplyEvent> CLOUD_APPLY = BukkitTranslations.simple(
      org.bukkit.event.entity.AreaEffectCloudApplyEvent.class, AreaEffectCloudApplyEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        AreaEffectCloud cloud = event.getEntity();
        ProjectileSource source = cloud.getSource();
        List<LivingRef> affected = new ArrayList<>();
        for (LivingEntity entity : event.getAffectedEntities()) {
          affected.add(living(entity));
        }
        AreaEffectCloudApplyEvent core = new AreaEffectCloudApplyEvent(entity(cloud),
            source instanceof org.bukkit.entity.Entity shooter ? entity(shooter) : null,
            GapEventMaps.key(cloud.getBasePotionType()), GapEventMaps.effects(cloud.getCustomEffects()), affected);
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

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
      EntityKnockbackEvent.Cause cause = GapEventMaps.knockbackCause(event.getCause());
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

  static final Translation<io.papermc.paper.event.entity.EntityInsideBlockEvent> INSIDE_BLOCK = BukkitTranslations.simple(
      io.papermc.paper.event.entity.EntityInsideBlockEvent.class, EntityInsideBlockEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        EntityInsideBlockEvent core = new EntityInsideBlockEvent(entity(event.getEntity()), block(event.getBlock()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.entity.EntityToggleGlideEvent> TOGGLE_GLIDE = new Translation<>(
      org.bukkit.event.entity.EntityToggleGlideEvent.class, List.of(EntityToggleGlideEvent.class)) {
    @Override
    public Class<? extends AdaptEvent> target(org.bukkit.event.entity.EntityToggleGlideEvent event) {
      return event.getEntity() instanceof LivingEntity ? EntityToggleGlideEvent.class : null;
    }

    @Override
    public void bridge(org.bukkit.event.entity.EntityToggleGlideEvent event, Poster poster) {
      boolean cancelled = event.isCancelled();
      EntityToggleGlideEvent core = new EntityToggleGlideEvent(living((LivingEntity) event.getEntity()), event.isGliding());
      core.setCancelled(cancelled);
      poster.post(core);
      cancel(event, cancelled, core);
    }
  };

  static final Translation<org.bukkit.event.entity.PiglinBarterEvent> PIGLIN_BARTER = BukkitTranslations.simple(
      org.bukkit.event.entity.PiglinBarterEvent.class, PiglinBarterEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        List<ItemStack> outcome = event.getOutcome();
        List<ItemRef> refs = GapEventMaps.items(outcome);
        PiglinBarterEvent core = new PiglinBarterEvent(entity(event.getEntity()), GapEventMaps.itemOrEmpty(event.getInput()), refs);
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
        if (!sameRefs(core.outcome(), refs)) {
          outcome.clear();
          for (ItemRef ref : core.outcome()) {
            if (ref != null && !ref.isEmpty()) {
              outcome.add(stack(ref));
            }
          }
        }
      });

  static final Translation<io.papermc.paper.event.entity.EntityEquipmentChangedEvent> EQUIPMENT_CHANGED = BukkitTranslations.simple(
      io.papermc.paper.event.entity.EntityEquipmentChangedEvent.class, EntityEquipmentChangedEvent.class, (event, poster) -> {
        Map<EquipmentSlot, EntityEquipmentChangedEvent.Change> changes = new EnumMap<>(EquipmentSlot.class);
        for (Map.Entry<org.bukkit.inventory.EquipmentSlot, EquipmentChange> entry : event.getEquipmentChanges().entrySet()) {
          EquipmentSlot slot = slot(entry.getKey());
          if (slot != null) {
            changes.put(slot, new EntityEquipmentChangedEvent.Change(GapEventMaps.itemOrEmpty(entry.getValue().oldItem()),
                GapEventMaps.itemOrEmpty(entry.getValue().newItem())));
          }
        }
        poster.post(new EntityEquipmentChangedEvent(living(event.getEntity()), changes));
      });

  static final Translation<io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent> TRANSPORT_TARGET =
      BukkitTranslations.simple(io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent.class,
          ItemTransportingEntityValidateTargetEvent.class, (event, poster) -> {
            boolean allowed = event.isAllowed();
            ItemTransportingEntityValidateTargetEvent core = new ItemTransportingEntityValidateTargetEvent(
                entity(event.getEntity()), block(event.getBlock()), allowed);
            poster.post(core);
            if (core.allowed() != allowed) {
              event.setAllowed(core.allowed());
            }
          });

  static final Translation<com.destroystokyo.paper.event.entity.EntityPathfindEvent> PATHFIND = BukkitTranslations.simple(
      com.destroystokyo.paper.event.entity.EntityPathfindEvent.class, EntityPathfindEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        EntityPathfindEvent core = new EntityPathfindEvent(entity(event.getEntity()), BukkitRefs.loc(event.getLoc()),
            entity(event.getTargetEntity()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  private static EquipmentSlot slot(org.bukkit.inventory.EquipmentSlot slot) {
    if (slot == null) {
      return null;
    }
    return switch (slot) {
      case HAND -> EquipmentSlot.MAIN_HAND;
      case OFF_HAND -> EquipmentSlot.OFF_HAND;
      case HEAD -> EquipmentSlot.HEAD;
      case CHEST -> EquipmentSlot.CHEST;
      case LEGS -> EquipmentSlot.LEGS;
      case FEET -> EquipmentSlot.FEET;
      case BODY -> EquipmentSlot.BODY;
      case SADDLE -> EquipmentSlot.SADDLE;
      default -> null;
    };
  }

  private static boolean sameRefs(List<ItemRef> after, List<ItemRef> before) {
    if (after.size() != before.size()) {
      return false;
    }
    for (int index = 0; index < after.size(); index++) {
      if (after.get(index) != before.get(index)) {
        return false;
      }
    }
    return true;
  }
}
