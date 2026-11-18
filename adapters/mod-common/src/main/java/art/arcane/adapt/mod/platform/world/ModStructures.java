package art.arcane.adapt.mod.platform.world;

import art.arcane.adapt.mod.platform.ModConversions;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.ChunkPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.ChunkStructures;
import art.arcane.adapt.platform.world.GeneratedStructure;
import art.arcane.adapt.platform.world.WorldRef;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Structure queries for a dimension, shaped like Bukkit's chunk structure, async chunk and locate calls. */
final class ModStructures {
    private ModStructures() {
    }

    /** Starts referenced by a chunk (loaded when needed), like {@code StructureManager#startsForStructure}. */
    static List<GeneratedStructure> structuresAt(ServerLevel level, int chunkX, int chunkZ) {
        LevelChunk chunk = level.getChunk(chunkX, chunkZ);
        List<GeneratedStructure> found = new ArrayList<>();
        for (Map.Entry<Structure, LongSet> entry : chunk.getAllReferences().entrySet()) {
            level.structureManager().fillStartsForStructure(entry.getKey(), entry.getValue(),
                (StructureStart start) -> found.add(structure(level, start)));
        }
        return found;
    }

    /**
     * Structure data of a generated chunk without generating it: the live chunk when it is loaded, else the stored
     * chunk data. Completes on the server thread; null when the chunk was never saved.
     */
    static CompletableFuture<ChunkStructures> generatedStructures(ServerLevel level, int chunkX, int chunkZ) {
        LevelChunk loaded = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (loaded != null) {
            return CompletableFuture.completedFuture(loaded(level, loaded));
        }
        return level.getChunkSource().chunkMap.read(new net.minecraft.world.level.ChunkPos(chunkX, chunkZ))
            .thenApplyAsync((Optional<CompoundTag> stored) -> stored.map((CompoundTag tag) -> stored(level, tag)).orElse(null),
                level.getServer());
    }

    static Loc locateNearest(WorldRef world, ServerLevel level, Loc origin, Id structureType, int radiusChunks,
                             boolean findUnexplored) {
        Optional<StructureType<?>> type = BuiltInRegistries.STRUCTURE_TYPE.getOptional(ModConversions.identifier(structureType));
        if (type.isEmpty()) {
            return null;
        }
        Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        List<Holder<Structure>> holders = new ArrayList<>();
        for (Structure structure : registry) {
            if (structure.type() == type.get()) {
                holders.add(registry.wrapAsHolder(structure));
            }
        }
        Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator().findNearestMapStructure(level,
            HolderSet.direct(holders), BlockPos.containing(origin.x(), origin.y(), origin.z()), radiusChunks, findUnexplored);
        if (found == null) {
            return null;
        }
        BlockPos pos = found.getFirst();
        return new Loc(world, pos.getX(), pos.getY(), pos.getZ());
    }

    private static ChunkStructures loaded(ServerLevel level, LevelChunk chunk) {
        List<GeneratedStructure> starts = new ArrayList<>();
        for (StructureStart start : chunk.getAllStarts().values()) {
            if (start.isValid()) {
                starts.add(structure(level, start));
            }
        }
        List<ChunkPos> references = new ArrayList<>();
        for (LongSet positions : chunk.getAllReferences().values()) {
            for (long position : positions) {
                references.add(ChunkPos.fromKey(position));
            }
        }
        return new ChunkStructures(starts, references);
    }

    private static ChunkStructures stored(ServerLevel level, CompoundTag chunk) {
        CompoundTag structures = chunk.getCompoundOrEmpty("structures");
        CompoundTag startTags = structures.getCompoundOrEmpty("starts");
        StructurePieceSerializationContext context = StructurePieceSerializationContext.fromLevel(level);
        List<GeneratedStructure> starts = new ArrayList<>();
        for (String key : startTags.keySet()) {
            StructureStart start = StructureStart.loadStaticStart(context, startTags.getCompoundOrEmpty(key), level.getSeed());
            if (start != null && start.isValid()) {
                starts.add(structure(level, start));
            }
        }
        CompoundTag referenceTags = structures.getCompoundOrEmpty("References");
        List<ChunkPos> references = new ArrayList<>();
        for (String key : referenceTags.keySet()) {
            for (long position : referenceTags.getLongArray(key).orElse(new long[0])) {
                references.add(ChunkPos.fromKey(position));
            }
        }
        return new ChunkStructures(starts, references);
    }

    private static GeneratedStructure structure(ServerLevel level, StructureStart start) {
        Structure structure = start.getStructure();
        Identifier structureKey = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(structure);
        Identifier typeKey = BuiltInRegistries.STRUCTURE_TYPE.getKey(structure.type());
        BoundingBox box = start.getBoundingBox();
        return new GeneratedStructure(ModConversions.id(structureKey), ModConversions.id(typeKey),
            new Box(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()));
    }
}
