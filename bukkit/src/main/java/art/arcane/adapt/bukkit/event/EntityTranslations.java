package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.BlockedDamage;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityBreedEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByBlockEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.EntityPickupItemEvent;
import art.arcane.adapt.platform.event.entity.EntityPotionEffectEvent;
import art.arcane.adapt.platform.event.entity.EntityRegainHealthEvent;
import art.arcane.adapt.platform.event.entity.EntityShootBowEvent;
import art.arcane.adapt.platform.event.entity.EntityTameEvent;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.compat.PaperApi;
import art.arcane.adapt.util.common.compat.PaperCompat;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static art.arcane.adapt.bukkit.event.BukkitEventMaps.block;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.cause;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.entity;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.item;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.living;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.player;
import static art.arcane.adapt.bukkit.event.BukkitTranslations.cancel;

final class EntityTranslations {
  private EntityTranslations() {
  }

  static List<Translation<?>> all() {
    return List.of(DAMAGE, DEATH, BREED, PICKUP_ITEM, POTION_EFFECT, REGAIN_HEALTH, SHOOT_BOW, TAME, TARGET,
        PROJECTILE_HIT, PROJECTILE_LAUNCH);
  }

  static final Translation<org.bukkit.event.entity.EntityDamageEvent> DAMAGE =
      new Translation<>(org.bukkit.event.entity.EntityDamageEvent.class,
          List.of(EntityDamageEvent.class, EntityDamageByEntityEvent.class, EntityDamageByBlockEvent.class)) {
        @Override
        public Class<? extends EntityDamageEvent> target(org.bukkit.event.entity.EntityDamageEvent event) {
          if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent) {
            return EntityDamageByEntityEvent.class;
          }
          return event instanceof org.bukkit.event.entity.EntityDamageByBlockEvent
              ? EntityDamageByBlockEvent.class
              : EntityDamageEvent.class;
        }

        @Override
        public void bridge(org.bukkit.event.entity.EntityDamageEvent event, Poster poster) {
          boolean cancelled = event.isCancelled();
          double damage = event.getDamage();
          EntityDamageEvent core = damageEvent(event, damage);
          core.setCancelled(cancelled);
          poster.post(core);
          cancel(event, cancelled, core);
          if (core.damage() != damage) {
            event.setDamage(core.damage());
          }
          if (core.ignoreReductions()) {
            ignoreReductions(event);
          }
        }
      };

  /** Zeroes every applicable reduction so the base damage lands in full. */
  @SuppressWarnings("deprecation")
  static void ignoreReductions(org.bukkit.event.entity.EntityDamageEvent event) {
    for (org.bukkit.event.entity.EntityDamageEvent.DamageModifier modifier
        : org.bukkit.event.entity.EntityDamageEvent.DamageModifier.values()) {
      if (modifier != org.bukkit.event.entity.EntityDamageEvent.DamageModifier.BASE && event.isApplicable(modifier)) {
        event.setDamage(modifier, 0D);
      }
    }
  }

  static EntityDamageEvent damageEvent(org.bukkit.event.entity.EntityDamageEvent event, double damage) {
    EntityRef victim = entity(event.getEntity());
    DamageCause cause = cause(event.getCause());
    Id damageType = Paper121Access.damageType(event);
    FinalDamage finalDamage = base -> {
      double current = event.getDamage();
      if (current == base) {
        return event.getFinalDamage();
      }
      event.setDamage(base);
      try {
        return event.getFinalDamage();
      } finally {
        event.setDamage(current);
      }
    };
    BlockedDamage blockedDamage = blockedDamage(event);
    EntityDamageEvent core;
    if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent byEntity) {
      core = new EntityDamageByEntityEvent(victim, entity(byEntity.getDamager()), entity(Paper121Access.causingEntity(event)),
          cause, damageType, damage, PaperCompat.isCritical(byEntity), finalDamage, blockedDamage);
    } else if (event instanceof org.bukkit.event.entity.EntityDamageByBlockEvent byBlock) {
      core = new EntityDamageByBlockEvent(victim, block(byBlock.getDamager()), cause, damageType, damage,
          finalDamage, blockedDamage);
    } else {
      core = new EntityDamageEvent(victim, cause, damageType, damage, finalDamage, blockedDamage);
    }
    Location damageLocation = Paper121Access.damageLocation(event);
    core.setSource(damageLocation == null ? null : BukkitRefs.loc(damageLocation), originalBaseDamage(event),
        Paper121Access.replay(event));
    return core;
  }

  @SuppressWarnings("deprecation")
  private static double originalBaseDamage(org.bukkit.event.entity.EntityDamageEvent event) {
    return event.getOriginalDamage(org.bukkit.event.entity.EntityDamageEvent.DamageModifier.BASE);
  }

  @SuppressWarnings("deprecation")
  private static BlockedDamage blockedDamage(org.bukkit.event.entity.EntityDamageEvent event) {
    org.bukkit.event.entity.EntityDamageEvent.DamageModifier blocking =
        org.bukkit.event.entity.EntityDamageEvent.DamageModifier.BLOCKING;
    if (!event.isApplicable(blocking)) {
      return BlockedDamage.NONE;
    }
    return base -> {
      double current = event.getDamage();
      if (current == base) {
        return -event.getDamage(blocking);
      }
      event.setDamage(base);
      try {
        return -event.getDamage(blocking);
      } finally {
        event.setDamage(current);
      }
    };
  }

  static final Translation<org.bukkit.event.entity.EntityDeathEvent> DEATH =
      new Translation<>(org.bukkit.event.entity.EntityDeathEvent.class,
          List.of(EntityDeathEvent.class, PlayerDeathEvent.class)) {
        @Override
        public Class<? extends EntityDeathEvent> target(org.bukkit.event.entity.EntityDeathEvent event) {
          return event instanceof org.bukkit.event.entity.PlayerDeathEvent ? PlayerDeathEvent.class : EntityDeathEvent.class;
        }

        @Override
        public boolean cancelled(org.bukkit.event.entity.EntityDeathEvent event) {
          return false;
        }

        @Override
        public void bridge(org.bukkit.event.entity.EntityDeathEvent event, Poster poster) {
          LivingEntity dead = event.getEntity();
          PlayerRef killer = player(dead.getKiller());
          EntityRef direct = entity(Paper121Access.directEntity(event));
          EntityRef causing = entity(Paper121Access.causingEntity(event));
          org.bukkit.event.entity.EntityDamageEvent last = dead.getLastDamageCause();
          DamageCause cause = last == null ? DamageCause.CUSTOM : cause(last.getCause());
          List<ItemStack> drops = event.getDrops();
          List<ItemRef> dropRefs = new ArrayList<>(drops.size());
          for (ItemStack drop : drops) {
            if (drop != null) {
              dropRefs.add(BukkitRefs.item(drop));
            }
          }
          int droppedExp = event.getDroppedExp();
          if (event instanceof org.bukkit.event.entity.PlayerDeathEvent death) {
            PlayerDeathEvent core = new PlayerDeathEvent(player(death.getEntity()), killer, direct, causing, cause, dropRefs,
                droppedExp, death.getKeepInventory(), death.getKeepLevel(), death.getNewLevel(), death.getNewExp(),
                death.getNewTotalExp());
            core.setPlatformCancelled(PaperApi.deathCancelled(event));
            poster.post(core);
            writeDeath(event, core, droppedExp);
            if (core.keepInventory() != death.getKeepInventory()) {
              death.setKeepInventory(core.keepInventory());
            }
            if (core.keepLevel() != death.getKeepLevel()) {
              death.setKeepLevel(core.keepLevel());
            }
            if (core.newLevel() != death.getNewLevel()) {
              death.setNewLevel(core.newLevel());
            }
            if (core.newExp() != death.getNewExp()) {
              death.setNewExp(core.newExp());
            }
            if (core.newTotalExp() != death.getNewTotalExp()) {
              death.setNewTotalExp(core.newTotalExp());
            }
            return;
          }
          EntityDeathEvent core = new EntityDeathEvent(living(dead), killer, direct, causing, cause, dropRefs, droppedExp);
          core.setPlatformCancelled(PaperApi.deathCancelled(event));
          poster.post(core);
          writeDeath(event, core, droppedExp);
        }
      };

  private static void writeDeath(org.bukkit.event.entity.EntityDeathEvent event, EntityDeathEvent core, int droppedExp) {
    List<ItemStack> written = new ArrayList<>(core.drops().size());
    for (ItemRef drop : core.drops()) {
      if (drop != null) {
        written.add(BukkitRefs.item(drop));
      }
    }
    List<ItemStack> drops = event.getDrops();
    if (!sameInstances(drops, written)) {
      drops.clear();
      drops.addAll(written);
    }
    if (core.droppedExp() != droppedExp) {
      event.setDroppedExp(core.droppedExp());
    }
  }

  /** True when both lists hold the same item instances in the same order. */
  static boolean sameInstances(List<ItemStack> current, List<ItemStack> written) {
    if (current.size() != written.size()) {
      return false;
    }
    for (int index = 0; index < current.size(); index++) {
      if (current.get(index) != written.get(index)) {
        return false;
      }
    }
    return true;
  }

  static final Translation<org.bukkit.event.entity.EntityBreedEvent> BREED = BukkitTranslations.simple(
      org.bukkit.event.entity.EntityBreedEvent.class, EntityBreedEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        int experience = event.getExperience();
        EntityBreedEvent core = new EntityBreedEvent(living(event.getEntity()), living(event.getMother()),
            living(event.getFather()), living(event.getBreeder()), item(event.getBredWith()), experience);
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
        if (core.experience() != experience) {
          event.setExperience(core.experience());
        }
      });

  static final Translation<org.bukkit.event.entity.EntityPickupItemEvent> PICKUP_ITEM = BukkitTranslations.simple(
      org.bukkit.event.entity.EntityPickupItemEvent.class, EntityPickupItemEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        EntityPickupItemEvent core = new EntityPickupItemEvent(living(event.getEntity()),
            BukkitRefs.itemEntity(event.getItem()), event.getRemaining());
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  @SuppressWarnings("deprecation")
  static final Translation<org.bukkit.event.entity.EntityPotionEffectEvent> POTION_EFFECT = new Translation<>(
      org.bukkit.event.entity.EntityPotionEffectEvent.class, List.of(EntityPotionEffectEvent.class)) {
    @Override
    public Class<EntityPotionEffectEvent> target(org.bukkit.event.entity.EntityPotionEffectEvent event) {
      return event.getEntity() instanceof LivingEntity ? EntityPotionEffectEvent.class : null;
    }

    @Override
    public void bridge(org.bukkit.event.entity.EntityPotionEffectEvent event, Poster poster) {
      boolean cancelled = event.isCancelled();
      EntityPotionEffectEvent core = new EntityPotionEffectEvent(living((LivingEntity) event.getEntity()),
          BukkitRefs.id(event.getModifiedType().getKey()), BukkitEventMaps.effect(event.getOldEffect()),
          BukkitEventMaps.effect(event.getNewEffect()), BukkitEventMaps.potionAction(event.getAction()),
          BukkitEventMaps.potionCause(event.getCause()));
      core.setCancelled(cancelled);
      poster.post(core);
      cancel(event, cancelled, core);
    }
  };

  static final Translation<org.bukkit.event.entity.EntityRegainHealthEvent> REGAIN_HEALTH = BukkitTranslations.simple(
      org.bukkit.event.entity.EntityRegainHealthEvent.class, EntityRegainHealthEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        double amount = event.getAmount();
        EntityRegainHealthEvent core = new EntityRegainHealthEvent(entity(event.getEntity()), amount,
            BukkitEventMaps.regainReason(event.getRegainReason()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
        if (core.amount() != amount) {
          event.setAmount(core.amount());
        }
      });

  static final Translation<org.bukkit.event.entity.EntityShootBowEvent> SHOOT_BOW = new Translation<>(
      org.bukkit.event.entity.EntityShootBowEvent.class, List.of(EntityShootBowEvent.class)) {
    @Override
    public Class<EntityShootBowEvent> target(org.bukkit.event.entity.EntityShootBowEvent event) {
      return event.getBow() == null ? null : EntityShootBowEvent.class;
    }

    @Override
    public void bridge(org.bukkit.event.entity.EntityShootBowEvent event, Poster poster) {
      boolean cancelled = event.isCancelled();
      boolean consume = event.shouldConsumeItem();
      EntityShootBowEvent core = new EntityShootBowEvent(living(event.getEntity()), item(event.getBow()),
          item(event.getConsumable()), entity(event.getProjectile()), BukkitEventMaps.hand(event.getHand()),
          event.getForce(), consume);
      core.setCancelled(cancelled);
      poster.post(core);
      cancel(event, cancelled, core);
      if (core.consumeItem() != consume) {
        event.setConsumeItem(core.consumeItem());
      }
    }
  };

  static final Translation<org.bukkit.event.entity.EntityTameEvent> TAME = new Translation<>(
      org.bukkit.event.entity.EntityTameEvent.class, List.of(EntityTameEvent.class)) {
    @Override
    public Class<EntityTameEvent> target(org.bukkit.event.entity.EntityTameEvent event) {
      return event.getOwner() == null ? null : EntityTameEvent.class;
    }

    @Override
    public void bridge(org.bukkit.event.entity.EntityTameEvent event, Poster poster) {
      boolean cancelled = event.isCancelled();
      EntityTameEvent core = new EntityTameEvent(living(event.getEntity()),
          event.getOwner() instanceof Player owner ? player(owner) : null, event.getOwner().getUniqueId());
      core.setCancelled(cancelled);
      poster.post(core);
      cancel(event, cancelled, core);
    }
  };

  static final Translation<org.bukkit.event.entity.EntityTargetEvent> TARGET = BukkitTranslations.simple(
      org.bukkit.event.entity.EntityTargetEvent.class, EntityTargetEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        Entity original = event.getTarget();
        LivingRef target = original instanceof LivingEntity living ? living(living) : null;
        EntityTargetEvent core = new EntityTargetEvent(entity(event.getEntity()), target,
            BukkitEventMaps.targetReason(event.getReason()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
        if (core.target() != target) {
          event.setTarget(core.target() == null ? null : BukkitRefs.entity(core.target()));
        }
      });

  static final Translation<org.bukkit.event.entity.ProjectileHitEvent> PROJECTILE_HIT = BukkitTranslations.simple(
      org.bukkit.event.entity.ProjectileHitEvent.class, ProjectileHitEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        ProjectileHitEvent core = new ProjectileHitEvent(BukkitRefs.projectile(event.getEntity()), entity(event.getHitEntity()),
            block(event.getHitBlock()), BukkitEventMaps.face(event.getHitBlockFace()));
        core.setCancelled(cancelled);
        ProjectileContact.post(event, () -> poster.post(core));
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.entity.ProjectileLaunchEvent> PROJECTILE_LAUNCH = BukkitTranslations.simple(
      org.bukkit.event.entity.ProjectileLaunchEvent.class, ProjectileLaunchEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        ProjectileLaunchEvent core = new ProjectileLaunchEvent(BukkitRefs.projectile(event.getEntity()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });
}
