package art.arcane.adapt.gameplay;

import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.volmlib.nativelib.chunk.ChunkPosition;
import art.arcane.volmlib.nativelib.chunk.GeneratedChunkStructures;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.StructureSearchResult;

/** Server API the fixture needs whose shape differs between Paper versions. */
public final class FixtureVersion {
    public static final Attribute MAX_HEALTH = Attribute.MAX_HEALTH;
    public static final Attribute KNOCKBACK_RESISTANCE = Attribute.KNOCKBACK_RESISTANCE;
    public static final Material SHORT_GRASS = Material.SHORT_GRASS;
    /** Server living entity class and its combat memory setters. */
    public static final String LIVING_ENTITY_CLASS = "net.minecraft.world.entity.LivingEntity";
    public static final String SET_LAST_HURT_MOB = "setLastHurtMob";
    public static final String SET_LAST_HURT_BY_MOB = "setLastHurtByMob";

    private FixtureVersion() {
    }

    public static void freezeDemoWorld(World world) {
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        world.setGameRule(GameRules.KEEP_INVENTORY, true);
        world.setGameRule(GameRules.FALL_DAMAGE, false);
        world.setGameRule(GameRules.SEND_COMMAND_FEEDBACK, false);
    }

    public static void resetPlateRules(World world, int randomTickSpeed) {
        world.setGameRule(GameRules.FALL_DAMAGE, false);
        world.setGameRule(GameRules.RANDOM_TICK_SPEED, randomTickSpeed);
        world.setGameRule(GameRules.MOB_GRIEFING, true);
    }

    public static Integer timeUntilBite(FishHook hook) {
        return hook.getTimeUntilBite();
    }

    public static Entity summonSnapshot(String snapshot, Location at) {
        return Bukkit.getEntityFactory().createEntitySnapshot(snapshot).createEntity(at);
    }

    /** Bounds of the generated structure the search found, with its native start prepared, or null. */
    public static BoundingBox generatedStructureBounds(World world, StructureSearchResult target) {
        int chunkX = target.getLocation().getBlockX() >> 4;
        int chunkZ = target.getLocation().getBlockZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Chunk chunk = world.getChunkAt(chunkX + dx, chunkZ + dz);
                GeneratedChunkStructures.Snapshot snapshot = NativeAdapters.require(GeneratedChunkStructures.class).read(chunk);
                for (GeneratedStructure structure : chunk.getStructures()) {
                    if (structure.getStructure().equals(target.getStructure())) {
                        prepareNativeStart(world, snapshot, structure);
                        return structure.getBoundingBox();
                    }
                }
            }
        }
        return null;
    }

    private static void prepareNativeStart(World world, GeneratedChunkStructures.Snapshot snapshot, GeneratedStructure structure) {
        NamespacedKey key = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE).getKey(structure.getStructure());
        BoundingBox bounds = structure.getBoundingBox();
        if (containsStart(snapshot, key, bounds)) {
            return;
        }
        if (snapshot.references().size() > 64) {
            throw new IllegalStateException("Structure approach exceeds its native start preparation budget");
        }
        for (ChunkPosition reference : snapshot.references()) {
            Chunk start = world.getChunkAt(reference.x(), reference.z());
            if (containsStart(NativeAdapters.require(GeneratedChunkStructures.class).read(start), key, bounds)) {
                Bukkit.getLogger().info("Demo generated native start prepared at " + reference.x() + " " + reference.z());
                return;
            }
        }
        throw new IllegalStateException("Located structure has no matching generated native start");
    }

    private static boolean containsStart(GeneratedChunkStructures.Snapshot snapshot, NamespacedKey key, BoundingBox expected) {
        for (GeneratedChunkStructures.Entry entry : snapshot.starts()) {
            GeneratedChunkStructures.Bounds bounds = entry.bounds();
            if (entry.structureKey().equals(key) && bounds.minX() == expected.getMinX() && bounds.minY() == expected.getMinY()
                    && bounds.minZ() == expected.getMinZ() && bounds.maxX() == expected.getMaxX()
                    && bounds.maxY() == expected.getMaxY() && bounds.maxZ() == expected.getMaxZ()) {
                return true;
            }
        }
        return false;
    }
}
