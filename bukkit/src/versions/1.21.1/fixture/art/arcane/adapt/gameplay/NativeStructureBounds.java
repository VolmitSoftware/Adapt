package art.arcane.adapt.gameplay;

import art.arcane.adapt.bukkit.platform.BukkitNatives;
import art.arcane.volmlib.nativelib.chunk.ChunkPosition;
import art.arcane.volmlib.nativelib.chunk.GeneratedChunkStructures;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.StructureSearchResult;

/**
 * Bounds of a located structure from the generated structure data Sixth Sense reads: the start held by a chunk around
 * the search result or by a chunk those chunks reference, so the start Sixth Sense will find is loaded.
 */
final class NativeStructureBounds {
    private static final int REFERENCE_BUDGET = 64;

    private NativeStructureBounds() {
    }

    static BoundingBox of(World world, StructureSearchResult target) {
        GeneratedChunkStructures structures = BukkitNatives.find(GeneratedChunkStructures.class)
                .orElseThrow(() -> new IllegalStateException("Generated structure data is unavailable"));
        NamespacedKey key = target.getStructure().getKey();
        int chunkX = target.getLocation().getBlockX() >> 4;
        int chunkZ = target.getLocation().getBlockZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                GeneratedChunkStructures.Snapshot snapshot = structures.read(world.getChunkAt(chunkX + dx, chunkZ + dz));
                BoundingBox own = start(snapshot, key);
                if (own != null) {
                    return own;
                }
                if (snapshot.references().size() > REFERENCE_BUDGET) {
                    throw new IllegalStateException("Structure approach exceeds its native start preparation budget");
                }
                for (ChunkPosition reference : snapshot.references()) {
                    BoundingBox referenced = start(structures.read(world.getChunkAt(reference.x(), reference.z())), key);
                    if (referenced != null) {
                        Bukkit.getLogger().info("Demo generated native start prepared at " + reference.x() + " " + reference.z());
                        return referenced;
                    }
                }
            }
        }
        return null;
    }

    private static BoundingBox start(GeneratedChunkStructures.Snapshot snapshot, NamespacedKey key) {
        for (GeneratedChunkStructures.Entry entry : snapshot.starts()) {
            if (entry.structureKey().equals(key)) {
                GeneratedChunkStructures.Bounds bounds = entry.bounds();
                return new BoundingBox(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
            }
        }
        return null;
    }
}
