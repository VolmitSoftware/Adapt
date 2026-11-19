package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.entity.DamageReplay;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Trident;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.potion.PotionData;

import java.util.Locale;

/**
 * Paper 1.20.1 has no damage source API: the damage type is derived from the cause and the damaging entity or block,
 * the causing entity is the projectile shooter or TNT igniter, and the source position is unknown. Potion types are
 * not keyed there, so the vanilla potion id is rebuilt from the type and its long or strong variant.
 */
final class Paper121Access {
  private Paper121Access() {
  }

  static Id damageType(EntityDamageEvent event) {
    Entity damager = event instanceof EntityDamageByEntityEvent byEntity ? byEntity.getDamager() : null;
    return Id.minecraft(switch (event.getCause()) {
      case KILL, SUICIDE -> "generic_kill";
      case WORLD_BORDER -> "outside_border";
      case CONTACT -> contactType(event instanceof EntityDamageByBlockEvent byBlock ? byBlock.getDamager() : null);
      case ENTITY_ATTACK -> damager instanceof Player ? "player_attack" : "mob_attack";
      case ENTITY_SWEEP_ATTACK -> "player_attack";
      case PROJECTILE -> pearlLanding(damager, event.getEntity()) ? "ender_pearl" : projectileType(damager);
      case SUFFOCATION -> "in_wall";
      case FALL -> pearlLanding(damager, event.getEntity()) ? "ender_pearl" : "fall";
      case FIRE -> "in_fire";
      case FIRE_TICK, MELTING -> "on_fire";
      case LAVA -> "lava";
      case DROWNING -> "drown";
      case BLOCK_EXPLOSION -> "explosion";
      case ENTITY_EXPLOSION -> causing(damager) instanceof Player ? "player_explosion" : "explosion";
      case VOID -> "out_of_world";
      case LIGHTNING -> "lightning_bolt";
      case STARVATION -> "starve";
      case POISON -> "magic";
      case MAGIC -> damager == null ? "magic" : "indirect_magic";
      case WITHER -> "wither";
      case FALLING_BLOCK -> "falling_block";
      case THORNS -> "thorns";
      case DRAGON_BREATH -> "dragon_breath";
      case FLY_INTO_WALL -> "fly_into_wall";
      case HOT_FLOOR -> "hot_floor";
      case CRAMMING -> "cramming";
      case DRYOUT -> "dry_out";
      case FREEZE -> "freeze";
      case SONIC_BOOM -> "sonic_boom";
      default -> "generic";
    });
  }

  /** The thrower hurt by its own landing pearl, which 26.x reports as damage type ender_pearl. */
  private static boolean pearlLanding(Entity damager, Entity victim) {
    return damager instanceof EnderPearl pearl && pearl.getShooter() == victim;
  }

  private static String contactType(Block block) {
    Material type = block == null ? null : block.getType();
    if (type == Material.SWEET_BERRY_BUSH) {
      return "sweet_berry_bush";
    }
    return type == Material.POINTED_DRIPSTONE ? "stalagmite" : "cactus";
  }

  private static String projectileType(Entity projectile) {
    if (projectile instanceof Trident) {
      return "trident";
    }
    if (projectile instanceof AbstractArrow) {
      return "arrow";
    }
    if (projectile instanceof WitherSkull) {
      return "wither_skull";
    }
    if (projectile instanceof Fireball) {
      return "fireball";
    }
    return "thrown";
  }

  private static Entity causing(Entity direct) {
    if (direct instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
      return shooter;
    }
    if (direct instanceof TNTPrimed tnt) {
      return tnt.getSource();
    }
    return direct;
  }

  static Entity causingEntity(EntityDamageEvent event) {
    return event instanceof EntityDamageByEntityEvent byEntity ? causing(byEntity.getDamager()) : null;
  }

  static Location damageLocation(EntityDamageEvent event) {
    return null;
  }

  static DamageReplay replay(EntityDamageEvent event) {
    Entity causing = causingEntity(event);
    return (LivingRef target, double amount) -> {
      if (causing == null) {
        BukkitRefs.living(target).damage(amount);
      } else {
        BukkitRefs.living(target).damage(amount, causing);
      }
    };
  }

  static Entity directEntity(EntityDeathEvent event) {
    return event.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent byEntity ? byEntity.getDamager() : null;
  }

  static Entity causingEntity(EntityDeathEvent event) {
    EntityDamageEvent last = event.getEntity().getLastDamageCause();
    return last == null ? null : causingEntity(last);
  }

  static Id basePotion(AreaEffectCloud cloud) {
    PotionData data = cloud.getBasePotionData();
    if (data == null) {
      return null;
    }
    String name = switch (data.getType()) {
      case UNCRAFTABLE -> "empty";
      case JUMP -> "leaping";
      case SPEED -> "swiftness";
      case INSTANT_HEAL -> "healing";
      case INSTANT_DAMAGE -> "harming";
      case REGEN -> "regeneration";
      default -> data.getType().name().toLowerCase(Locale.ROOT);
    };
    if (data.isUpgraded()) {
      return Id.minecraft("strong_" + name);
    }
    return Id.minecraft(data.isExtended() ? "long_" + name : name);
  }

  static String renameText(PrepareAnvilEvent event) {
    return event.getInventory().getRenameText();
  }

  static int repairCost(PrepareAnvilEvent event) {
    return event.getInventory().getRepairCost();
  }

  static void setRepairCost(PrepareAnvilEvent event, int cost) {
    event.getInventory().setRepairCost(cost);
  }
}
