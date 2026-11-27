package art.arcane.adapt.api.mutation;

import art.arcane.adapt.api.text.Text;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A hand-written replacement for the generated description of one mutation slot, registered with
 * {@code ContentRegistrar.pairText}.
 *
 * @param description        the text shown; a Barter description starts after the gesture, which is prefixed
 * @param blocks             the blocks the adaptation runs instead of the recipe's; empty keeps the recipe's
 * @param stateWindowSeconds above 0, the State blocks count when they held at any point in that many seconds
 */
@ApiStatus.Experimental
public record PairText(Text description, Optional<List<BlockKey>> blocks, int stateWindowSeconds) {
  public PairText {
    Objects.requireNonNull(description, "description");
    blocks = Objects.requireNonNull(blocks, "blocks").map(List::copyOf);
    if (stateWindowSeconds < 0) {
      throw new IllegalArgumentException("A state window is 0 or more seconds");
    }
  }

  /** Replaces the description only. */
  public static PairText of(Text description) {
    return new PairText(description, Optional.empty(), 0);
  }

  /** Replaces the description and lets the State count when it held in the last {@code seconds}. */
  public static PairText recentState(Text description, int seconds) {
    return new PairText(description, Optional.empty(), seconds);
  }

  /** A copy that runs {@code replacement} instead of the recipe's blocks. */
  public PairText withBlocks(List<BlockKey> replacement) {
    return new PairText(description, Optional.of(replacement), stateWindowSeconds);
  }
}
