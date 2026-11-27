package art.arcane.adapt.platform.world;

import art.arcane.adapt.platform.value.ChunkPos;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * Structure starts stored in one generated chunk and the chunks holding the starts it references.
 *
 * @param starts structures whose start is stored in this chunk
 * @param references chunks holding the starts of other structures that reach into this chunk
 */
@ApiStatus.AvailableSince("1.0.0")
public record ChunkStructures(List<GeneratedStructure> starts, List<ChunkPos> references) {
  public ChunkStructures {
    starts = List.copyOf(starts);
    references = List.copyOf(references);
  }
}
