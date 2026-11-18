package art.arcane.adapt.mod.event;

import art.arcane.adapt.platform.event.entity.DamageCause;

/**
 * Paper's damage cause table (CraftEventFactory#handleEntityDamageEvent) by vanilla damage type path, for Paper 26.2 and
 * 26.3; overlays replace it where Paper differs. {@code type} is empty for damage types outside the minecraft namespace.
 */
final class DamageCauseRules {
    /** Paper marks the drowning of water animals out of water as {@code DRYOUT}. */
    static final boolean WATER_ANIMAL_DRYOUT = true;

    private DamageCauseRules() {
    }

    /** True when Paper reports this damage as block damage even without a known block. */
    static boolean alwaysByBlock(String type) {
        return switch (type) {
            case "out_of_world", "lava", "bad_respawn_point" -> true;
            default -> false;
        };
    }

    /**
     * The cause Paper reports. {@code damager} is the direct entity, {@code byBlock} is true for block damage and
     * {@code known} is the cause the call site fixes (sweep, poison, melting, dry-out) or null. The sulfur cube's
     * contact damage is Paper's known {@code CONTACT}.
     */
    static DamageCause cause(String type, DamageDealer damager, boolean byBlock, DamageCause known) {
        DamageDealer dealer = damager == DamageDealer.NONE ? DamageDealer.implied(type) : damager;
        if (dealer != DamageDealer.NONE) {
            if (known != null) {
                return known;
            }
            if ("sulfur_cube_hot".equals(type)) {
                return DamageCause.CONTACT;
            }
            if ("fireworks".equals(type) || "explosion".equals(type) || "player_explosion".equals(type)) {
                return dealer == DamageDealer.PRIMED_TNT ? DamageCause.BLOCK_EXPLOSION : DamageCause.ENTITY_EXPLOSION;
            }
            if (dealer.projectileCause() != null) {
                return dealer.projectileCause();
            }
            return switch (type) {
                case "thorns" -> DamageCause.THORNS;
                case "sonic_boom" -> DamageCause.SONIC_BOOM;
                case "falling_stalactite", "falling_block", "falling_anvil" -> DamageCause.FALLING_BLOCK;
                case "lightning_bolt" -> DamageCause.LIGHTNING;
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
                case "cactus", "sweet_berry_bush", "stalagmite", "hot_floor", "campfire" -> DamageCause.CONTACT;
                case "magic" -> DamageCause.MAGIC;
                case "in_fire" -> DamageCause.FIRE;
                case "bad_respawn_point" -> DamageCause.BLOCK_EXPLOSION;
                default -> DamageCause.CUSTOM;
            };
        }
        return DamageDealer.environmentCause(type);
    }
}
