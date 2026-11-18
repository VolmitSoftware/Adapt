package art.arcane.adapt.platform.world;

import art.arcane.adapt.platform.value.ChunkPos;

import java.util.List;

/** Structure starts stored in one generated chunk and the chunks holding the starts it references. */
public record ChunkStructures(List<GeneratedStructure> starts, List<ChunkPos> references) {
  public ChunkStructures {
    starts = List.copyOf(starts);
    references = List.copyOf(references);
  }
}
