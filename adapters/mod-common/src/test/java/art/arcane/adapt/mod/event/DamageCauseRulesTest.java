package art.arcane.adapt.mod.event;

import art.arcane.adapt.mod.AdaptModIdentity;
import art.arcane.adapt.platform.event.entity.DamageCause;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every vanilla damage type against Paper's CraftEventFactory cause table for the target version (null: type absent). */
class DamageCauseRulesTest {
    private static final boolean PAPER_26_1 = "26.1.2".equals(AdaptModIdentity.MINECRAFT_TARGET);

    private record Row(String type, DamageDealer dealer, boolean block, DamageCause known, DamageCause paper26_1, DamageCause paper26_2) {
        DamageCause expected() {
            return PAPER_26_1 ? paper26_1 : paper26_2;
        }
    }

    private static Row row(String type, DamageDealer dealer, DamageCause both) {
        return new Row(type, dealer, DamageCauseRules.alwaysByBlock(type), null, both, both);
    }

    private static Row row(String type, DamageDealer dealer, DamageCause paper26_1, DamageCause paper26_2) {
        return new Row(type, dealer, DamageCauseRules.alwaysByBlock(type), null, paper26_1, paper26_2);
    }

    private static Row atBlock(String type, DamageCause paper26_1, DamageCause paper26_2) {
        return new Row(type, DamageDealer.NONE, true, null, paper26_1, paper26_2);
    }

    private static Row known(String type, DamageDealer dealer, DamageCause cause) {
        return new Row(type, dealer, false, cause, cause, cause);
    }

    private static final List<Row> TABLE = List.of(
        row("arrow", DamageDealer.PROJECTILE, DamageCause.PROJECTILE),
        row("trident", DamageDealer.PROJECTILE, DamageCause.PROJECTILE),
        row("thrown", DamageDealer.PROJECTILE, DamageCause.PROJECTILE),
        row("mob_projectile", DamageDealer.PROJECTILE, DamageCause.PROJECTILE),
        row("spit", DamageDealer.PROJECTILE, DamageCause.PROJECTILE),
        row("fireball", DamageDealer.PROJECTILE, DamageCause.PROJECTILE),
        row("unattributed_fireball", DamageDealer.PROJECTILE, DamageCause.PROJECTILE),
        row("wither_skull", DamageDealer.PROJECTILE, DamageCause.PROJECTILE),
        row("wind_charge", DamageDealer.PROJECTILE, DamageCause.PROJECTILE),
        row("indirect_magic", DamageDealer.THROWN_POTION, DamageCause.MAGIC),
        row("indirect_magic", DamageDealer.OTHER, DamageCause.ENTITY_ATTACK),
        row("magic", DamageDealer.OTHER, DamageCause.MAGIC),
        row("magic", DamageDealer.NONE, DamageCause.MAGIC),
        atBlock("magic", DamageCause.MAGIC, DamageCause.MAGIC),
        row("ender_pearl", DamageDealer.NONE, DamageCause.PROJECTILE),
        row("lightning_bolt", DamageDealer.NONE, DamageCause.LIGHTNING),
        row("player_attack", DamageDealer.OTHER, DamageCause.ENTITY_ATTACK),
        row("mob_attack", DamageDealer.OTHER, DamageCause.ENTITY_ATTACK),
        row("mob_attack_no_aggro", DamageDealer.OTHER, DamageCause.ENTITY_ATTACK),
        row("spear", DamageDealer.OTHER, DamageCause.ENTITY_ATTACK),
        row("sting", DamageDealer.OTHER, DamageCause.ENTITY_ATTACK),
        row("mace_smash", DamageDealer.OTHER, DamageCause.ENTITY_ATTACK),
        row("generic", DamageDealer.OTHER, DamageCause.ENTITY_ATTACK),
        row("generic", DamageDealer.NONE, DamageCause.CUSTOM),
        row("", DamageDealer.OTHER, DamageCause.ENTITY_ATTACK),
        row("", DamageDealer.PROJECTILE, DamageCause.PROJECTILE),
        row("", DamageDealer.NONE, DamageCause.CUSTOM),
        row("thorns", DamageDealer.OTHER, DamageCause.THORNS),
        row("sonic_boom", DamageDealer.OTHER, DamageCause.SONIC_BOOM),
        row("falling_block", DamageDealer.OTHER, DamageCause.FALLING_BLOCK),
        row("falling_anvil", DamageDealer.OTHER, DamageCause.FALLING_BLOCK),
        row("falling_stalactite", DamageDealer.OTHER, DamageCause.FALLING_BLOCK),
        row("dragon_breath", DamageDealer.OTHER, DamageCause.DRAGON_BREATH),
        row("dragon_breath", DamageDealer.NONE, DamageCause.CUSTOM),
        row("explosion", DamageDealer.NONE, DamageCause.BLOCK_EXPLOSION, DamageCause.CUSTOM),
        row("explosion", DamageDealer.PRIMED_TNT, DamageCause.BLOCK_EXPLOSION),
        row("explosion", DamageDealer.OTHER, DamageCause.ENTITY_EXPLOSION),
        row("player_explosion", DamageDealer.PRIMED_TNT, DamageCause.BLOCK_EXPLOSION),
        row("player_explosion", DamageDealer.OTHER, DamageCause.ENTITY_EXPLOSION),
        row("fireworks", DamageDealer.PROJECTILE, DamageCause.ENTITY_EXPLOSION),
        row("fireworks", DamageDealer.NONE, DamageCause.BLOCK_EXPLOSION, DamageCause.CUSTOM),
        row("bad_respawn_point", DamageDealer.NONE, DamageCause.BLOCK_EXPLOSION),
        row("out_of_world", DamageDealer.NONE, DamageCause.VOID),
        row("lava", DamageDealer.NONE, DamageCause.LAVA),
        atBlock("lava", DamageCause.LAVA, DamageCause.LAVA),
        atBlock("cactus", DamageCause.CONTACT, DamageCause.CONTACT),
        atBlock("sweet_berry_bush", DamageCause.CONTACT, DamageCause.CONTACT),
        atBlock("stalagmite", DamageCause.CONTACT, DamageCause.CONTACT),
        atBlock("hot_floor", DamageCause.HOT_FLOOR, DamageCause.CONTACT),
        atBlock("campfire", DamageCause.CAMPFIRE, DamageCause.CONTACT),
        atBlock("in_fire", DamageCause.FIRE, DamageCause.FIRE),
        row("cactus", DamageDealer.NONE, DamageCause.CUSTOM),
        row("hot_floor", DamageDealer.NONE, DamageCause.CUSTOM),
        row("campfire", DamageDealer.NONE, DamageCause.CUSTOM),
        row("in_fire", DamageDealer.NONE, DamageCause.FIRE),
        row("on_fire", DamageDealer.NONE, DamageCause.FIRE_TICK),
        row("starve", DamageDealer.NONE, DamageCause.STARVATION),
        row("wither", DamageDealer.NONE, DamageCause.WITHER),
        row("in_wall", DamageDealer.NONE, DamageCause.SUFFOCATION),
        row("drown", DamageDealer.NONE, DamageCause.DROWNING),
        row("fall", DamageDealer.NONE, DamageCause.FALL),
        row("fly_into_wall", DamageDealer.NONE, DamageCause.FLY_INTO_WALL),
        row("cramming", DamageDealer.NONE, DamageCause.CRAMMING),
        row("dry_out", DamageDealer.NONE, DamageCause.DRYOUT),
        row("freeze", DamageDealer.NONE, DamageCause.FREEZE),
        row("generic_kill", DamageDealer.NONE, DamageCause.KILL),
        row("outside_border", DamageDealer.NONE, DamageCause.WORLD_BORDER),
        row("sulfur_cube_hot", DamageDealer.NONE, null, DamageCause.CONTACT),
        row("sulfur_cube_hot", DamageDealer.OTHER, null, DamageCause.CONTACT),
        known("player_attack", DamageDealer.OTHER, DamageCause.ENTITY_SWEEP_ATTACK),
        known("magic", DamageDealer.NONE, DamageCause.POISON),
        known("on_fire", DamageDealer.NONE, DamageCause.MELTING),
        known("drown", DamageDealer.NONE, DamageCause.DRYOUT)
    );

    @Test
    void everyVanillaDamageTypeMatchesPaper() {
        List<String> mismatches = new ArrayList<>();
        for (Row row : TABLE) {
            if (row.expected() == null) {
                continue;
            }
            DamageCause actual = DamageCauseRules.cause(row.type(), row.dealer(), row.block(), row.known());
            if (actual != row.expected()) {
                mismatches.add(row + " -> " + actual);
            }
        }
        assertTrue(mismatches.isEmpty(), String.join("\n", mismatches));
    }

    @Test
    void blockDamageWithoutABlockFollowsPaper() {
        assertTrue(DamageCauseRules.alwaysByBlock("out_of_world"));
        assertTrue(DamageCauseRules.alwaysByBlock("lava"));
        assertTrue(DamageCauseRules.alwaysByBlock("bad_respawn_point"));
        assertEquals(PAPER_26_1, DamageCauseRules.alwaysByBlock("explosion"));
        assertEquals(PAPER_26_1, DamageCauseRules.alwaysByBlock("fireworks"));
        assertEquals(false, DamageCauseRules.alwaysByBlock("hot_floor"));
        assertEquals(false, DamageCauseRules.alwaysByBlock("in_fire"));
    }

    @Test
    void waterAnimalsDryOutFromPaper262() {
        assertEquals(!PAPER_26_1, DamageCauseRules.WATER_ANIMAL_DRYOUT);
        assertEquals(PAPER_26_1 ? null : DamageCause.DRYOUT, KnownDamageCause.dryOut());
    }
}
