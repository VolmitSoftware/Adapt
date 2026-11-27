package art.arcane.adapt.api.mutation;

import org.jetbrains.annotations.ApiStatus;

import java.util.Optional;
import java.util.Set;

/**
 * How a player casts Barter. A pair uses the first gesture, in declaration order, that neither parent reserves, so
 * casting Barter never also sets off one of the parents' own abilities.
 *
 * <p>A skill reserves a gesture in its {@link MutationKit} when one of its abilities (original, upgrade or block,
 * under any control preference and with any item in hand) fires when the gesture is made.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.Experimental
public enum BarterGesture {
  SNEAK_SWAP,
  DOUBLE_SNEAK,
  SNEAK_LEFT_CLICK,
  SNEAK_SCROLL;

  /** The gesture of a pair, or empty when the two skills reserve all four between them; such a pair has no Barter. */
  public static Optional<BarterGesture> of(Set<BarterGesture> leadReserved, Set<BarterGesture> partnerReserved) {
    for (BarterGesture gesture : values()) {
      if (!leadReserved.contains(gesture) && !partnerReserved.contains(gesture)) {
        return Optional.of(gesture);
      }
    }
    return Optional.empty();
  }
}
