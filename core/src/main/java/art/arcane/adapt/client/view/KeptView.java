package art.arcane.adapt.client.view;

import java.util.Objects;

/**
 * An original adaptation that works for the player without being learned, and the level it works at. {@code ownerId}
 * is the mutated skill that keeps it, or the held upgrade that lends it a level while its own code still runs.
 */
public record KeptView(String ownerId, String adaptationId, int level) {
  public KeptView {
    Objects.requireNonNull(ownerId, "ownerId");
    Objects.requireNonNull(adaptationId, "adaptationId");
  }
}
