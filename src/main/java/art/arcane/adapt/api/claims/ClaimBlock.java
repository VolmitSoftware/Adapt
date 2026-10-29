package art.arcane.adapt.api.claims;

import java.util.Objects;
import java.util.UUID;

public record ClaimBlock(UUID worldId, int x, int y, int z) {
  public ClaimBlock {
    Objects.requireNonNull(worldId);
  }

  public ClaimChunk chunk() {
    return new ClaimChunk(worldId, Math.floorDiv(x, 16), Math.floorDiv(z, 16));
  }
}
