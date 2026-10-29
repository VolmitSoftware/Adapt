package art.arcane.adapt.api.claims;

import java.util.Objects;
import java.util.UUID;

public record ClaimAttempt(UUID player, ClaimBlock block, ClaimAction action, String blockState,
                           long timestamp, boolean externallyAllowed) {
  public ClaimAttempt {
    Objects.requireNonNull(player);
    Objects.requireNonNull(block);
    Objects.requireNonNull(action);
    Objects.requireNonNull(blockState);
  }
}
