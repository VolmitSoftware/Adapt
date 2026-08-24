package art.arcane.adapt.api.world;

import java.util.Objects;
import java.util.UUID;

public record FencedPlayerSnapshot(UUID playerId, UUID ownerToken, long epoch, long sequence,
                                   String json) {
  public FencedPlayerSnapshot {
    Objects.requireNonNull(playerId);
    Objects.requireNonNull(ownerToken);
    Objects.requireNonNull(json);
    if (epoch < 1L) {
      throw new IllegalArgumentException("Fence epoch must be positive");
    }
    if (sequence < 1L) {
      throw new IllegalArgumentException("Snapshot sequence must be positive");
    }
  }

  public boolean belongsTo(UUID candidateOwnerToken, long candidateEpoch) {
    return ownerToken.equals(candidateOwnerToken) && epoch == candidateEpoch;
  }
}
