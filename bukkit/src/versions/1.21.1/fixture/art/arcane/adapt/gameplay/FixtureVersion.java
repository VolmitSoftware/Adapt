package art.arcane.adapt.gameplay;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.StructureSearchResult;

/**
 * Server API the fixture needs on Paper 1.21.1: game rules by their old names (no fire spread radius, so fire tick is
 * off) and generic attribute names. Structure bounds come from this jar's generated structure natives.
 */
public final class FixtureVersion {
    public static final Attribute MAX_HEALTH = Attribute.GENERIC_MAX_HEALTH;
    public static final Attribute KNOCKBACK_RESISTANCE = Attribute.GENERIC_KNOCKBACK_RESISTANCE;
    public static final Material SHORT_GRASS = Material.SHORT_GRASS;
    /** Server living entity class and its combat memory setters. */
    public static final String LIVING_ENTITY_CLASS = "net.minecraft.world.entity.LivingEntity";
    public static final String SET_LAST_HURT_MOB = "setLastHurtMob";
    public static final String SET_LAST_HURT_BY_MOB = "setLastHurtByMob";

    private FixtureVersion() {
    }

    public static void freezeDemoWorld(World world) {
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.FALL_DAMAGE, false);
        world.setGameRule(GameRule.SEND_COMMAND_FEEDBACK, false);
    }

    public static void resetPlateRules(World world, int randomTickSpeed) {
        world.setGameRule(GameRule.FALL_DAMAGE, false);
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, randomTickSpeed);
        world.setGameRule(GameRule.MOB_GRIEFING, true);
    }

    public static Integer timeUntilBite(FishHook hook) {
        return hook.getTimeUntilBite();
    }

    public static Entity summonSnapshot(String snapshot, Location at) {
        return Bukkit.getEntityFactory().createEntitySnapshot(snapshot).createEntity(at);
    }

    public static BoundingBox generatedStructureBounds(World world, StructureSearchResult target) {
        return NativeStructureBounds.of(world, target);
    }
}
