package art.arcane.adapt.mod.event;

import art.arcane.adapt.platform.event.entity.DamageCause;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownPotion;

/** The kind of entity Paper treats as the damager of a hit, as far as its cause table distinguishes them. */
enum DamageDealer {
    NONE,
    PRIMED_TNT,
    THROWN_POTION,
    PROJECTILE,
    OTHER;

    static DamageDealer of(Entity direct) {
        if (direct == null) {
            return NONE;
        }
        if (direct instanceof PrimedTnt) {
            return PRIMED_TNT;
        }
        if (direct instanceof ThrownPotion) {
            return THROWN_POTION;
        }
        return direct instanceof Projectile ? PROJECTILE : OTHER;
    }

    /** The damager Paper attaches where vanilla sets no entity: the bolt, the thrown pearl, the sulfur cube. */
    static DamageDealer implied(String type) {
        return switch (type) {
            case "lightning_bolt", "sulfur_cube_hot" -> OTHER;
            case "ender_pearl" -> PROJECTILE;
            default -> NONE;
        };
    }

    /** The cause Paper gives any hit from this damager regardless of the damage type, or null. */
    DamageCause projectileCause() {
        return switch (this) {
            case THROWN_POTION -> DamageCause.MAGIC;
            case PROJECTILE -> DamageCause.PROJECTILE;
            default -> null;
        };
    }

    /** Paper's cause for damage with neither a damager nor a block. */
    static DamageCause environmentCause(String type) {
        return switch (type) {
            case "in_fire" -> DamageCause.FIRE;
            case "starve" -> DamageCause.STARVATION;
            case "wither" -> DamageCause.WITHER;
            case "in_wall" -> DamageCause.SUFFOCATION;
            case "drown" -> DamageCause.DROWNING;
            case "on_fire" -> DamageCause.FIRE_TICK;
            case "magic" -> DamageCause.MAGIC;
            case "fall" -> DamageCause.FALL;
            case "fly_into_wall" -> DamageCause.FLY_INTO_WALL;
            case "cramming" -> DamageCause.CRAMMING;
            case "dry_out" -> DamageCause.DRYOUT;
            case "freeze" -> DamageCause.FREEZE;
            case "generic_kill" -> DamageCause.KILL;
            case "outside_border" -> DamageCause.WORLD_BORDER;
            default -> DamageCause.CUSTOM;
        };
    }
}
