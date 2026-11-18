package art.arcane.adapt.mod.event;

import art.arcane.adapt.platform.event.entity.DamageCause;

/**
 * Paper 26.1.2's damage cause table (CraftEventFactory#handleEntityDamageEvent) by vanilla damage type path: every
 * explosion is resolved before the damager, and explosions without a damager count as block damage.
 */
final class DamageCauseRules {
    /** Paper 26.1.2 reports the drowning of water animals out of water as plain {@code DROWNING}. */
    static final boolean WATER_ANIMAL_DRYOUT = false;

    private DamageCauseRules() {
    }

    /** True when Paper reports this damage as block damage even without a known block. */
    static boolean alwaysByBlock(String type) {
        return explosion(type) || "out_of_world".equals(type) || "lava".equals(type);
    }

    /**
     * The cause Paper reports. {@code damager} is the direct entity, {@code byBlock} is true for block damage and
     * {@code known} is the cause the call site fixes (sweep, poison, melting) or null.
     */
    static DamageCause cause(String type, DamageDealer damager, boolean byBlock, DamageCause known) {
        DamageDealer dealer = damager == DamageDealer.NONE ? DamageDealer.implied(type) : damager;
        if (explosion(type)) {
            return dealer == DamageDealer.NONE || dealer == DamageDealer.PRIMED_TNT ? DamageCause.BLOCK_EXPLOSION : DamageCause.ENTITY_EXPLOSION;
        }
        if (dealer != DamageDealer.NONE) {
            if (known != null) {
                return known;
            }
            if (dealer.projectileCause() != null) {
                return dealer.projectileCause();
            }
            return switch (type) {
                case "thorns" -> DamageCause.THORNS;
                case "sonic_boom" -> DamageCause.SONIC_BOOM;
                case "falling_stalactite", "falling_block", "falling_anvil" -> DamageCause.FALLING_BLOCK;
                case "lightning_bolt" -> DamageCause.LIGHTNING;
                case "fall" -> DamageCause.FALL;
                case "dragon_breath" -> DamageCause.DRAGON_BREATH;
                case "magic" -> DamageCause.MAGIC;
                default -> DamageCause.ENTITY_ATTACK;
            };
        }
        if ("out_of_world".equals(type)) {
            return DamageCause.VOID;
        }
        if ("lava".equals(type)) {
            return DamageCause.LAVA;
        }
        if (known != null) {
            return known;
        }
        if (byBlock) {
            return switch (type) {
                case "cactus", "sweet_berry_bush", "stalagmite", "falling_stalactite", "falling_anvil" -> DamageCause.CONTACT;
                case "hot_floor" -> DamageCause.HOT_FLOOR;
                case "magic" -> DamageCause.MAGIC;
                case "in_fire" -> DamageCause.FIRE;
                case "campfire" -> DamageCause.CAMPFIRE;
                default -> DamageCause.CUSTOM;
            };
        }
        return DamageDealer.environmentCause(type);
    }

    private static boolean explosion(String type) {
        return switch (type) {
            case "fireworks", "explosion", "player_explosion", "bad_respawn_point" -> true;
            default -> false;
        };
    }
}
