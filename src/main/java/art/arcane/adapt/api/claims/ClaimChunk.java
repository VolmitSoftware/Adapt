package art.arcane.adapt.api.claims;

import java.util.Objects;
import java.util.UUID;

public record ClaimChunk(UUID worldId, int x, int z) {
  public ClaimChunk {
    Objects.requireNonNull(worldId);
  }

  public boolean sharesEdge(ClaimChunk other) {
    return worldId.equals(other.worldId())
        && Math.abs((long) x - other.x()) + Math.abs((long) z - other.z()) == 1;
  }
}
