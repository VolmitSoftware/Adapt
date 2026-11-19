package art.arcane.adapt.gameplay;

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
 * Server API the fixture needs on Paper 1.20.1: game rules by their old names (no fire spread radius, so fire tick is
 * off), generic attribute names and plain grass. Fish hooks do not expose the bite countdown and entities cannot be
 * created from a snapshot. Structure bounds come from this jar's generated structure natives.
 */
public final class FixtureVersion {
    public static final Attribute MAX_HEALTH = Attribute.GENERIC_MAX_HEALTH;
    public static final Attribute KNOCKBACK_RESISTANCE = Attribute.GENERIC_KNOCKBACK_RESISTANCE;
    public static final Material SHORT_GRASS = Material.GRASS;
    /** Server living entity class and its combat memory setters (Spigot-obfuscated names on 1.20.1). */
    public static final String LIVING_ENTITY_CLASS = "net.minecraft.world.entity.EntityLiving";
    public static final String SET_LAST_HURT_MOB = "x";
    public static final String SET_LAST_HURT_BY_MOB = "a";

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
        return null;
    }

    public static Entity summonSnapshot(String snapshot, Location at) {
        throw new IllegalStateException("Entity snapshots are unavailable on Paper 1.20.1");
    }

    public static BoundingBox generatedStructureBounds(World world, StructureSearchResult target) {
        return NativeStructureBounds.of(world, target);
    }
}
