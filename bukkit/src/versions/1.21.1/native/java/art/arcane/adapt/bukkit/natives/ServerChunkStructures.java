package art.arcane.adapt.bukkit.natives;

import art.arcane.volmlib.nativelib.chunk.ChunkPosition;
import art.arcane.volmlib.nativelib.chunk.GeneratedChunkStructures;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Structure starts and references of a loaded chunk, read on the chunk's owning thread like the 26.x native reader. */
final class ServerChunkStructures implements GeneratedChunkStructures {
  @Override
  public Snapshot read(Chunk chunk) {
    if (!Bukkit.isOwnedByCurrentRegion(chunk.getWorld(), chunk.getX(), chunk.getZ())) {
      throw new IllegalStateException("Generated structure metadata requires the chunk's owning thread");
    }
    ServerLevel level = Objects.requireNonNull(ServerHandles.level(chunk.getWorld()), "level");
    LevelChunk loaded = level.getChunkIfLoaded(chunk.getX(), chunk.getZ());
    if (loaded == null) {
      return new Snapshot(List.of(), List.of());
    }
    List<Entry> starts = new ArrayList<>(loaded.getAllStarts().size());
    for (StructureStart start : loaded.getAllStarts().values()) {
      if (start.isValid()) {
        Structure structure = start.getStructure();
        BoundingBox box = start.getBoundingBox();
        starts.add(new Entry(key(level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(structure)),
            key(BuiltInRegistries.STRUCTURE_TYPE.getKey(structure.type())),
            new Bounds(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())));
      }
    }
    List<ChunkPosition> references = new ArrayList<>();
    for (LongSet positions : loaded.getAllReferences().values()) {
      for (long position : positions) {
        references.add(new ChunkPosition((int) position, (int) (position >> 32)));
      }
    }
    return new Snapshot(starts, references);
  }

  private static NamespacedKey key(ResourceLocation location) {
    return new NamespacedKey(Objects.requireNonNull(location, "registry key").getNamespace(), location.getPath());
  }
}
