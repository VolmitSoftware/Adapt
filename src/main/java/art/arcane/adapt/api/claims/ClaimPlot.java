package art.arcane.adapt.api.claims;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ClaimPlot(UUID owner, UUID cityId, ClaimBlock anchor, Set<ClaimChunk> chunks) {
  public ClaimPlot {
    Objects.requireNonNull(owner);
    Objects.requireNonNull(cityId);
    Objects.requireNonNull(anchor);
    chunks = Set.copyOf(chunks);
    if (!chunks.equals(ClaimSnapshot.footprint(anchor.chunk()))) {
      throw new IllegalArgumentException("A personal plot must cover its anchor's complete 3x3 footprint");
    }
  }
}
