package art.arcane.adapt.api.mutation;

import org.jetbrains.annotations.ApiStatus;

/**
 * The five adaptations every mutation owns, each built from the two parents' blocks in a fixed way.
 *
 * <ul>
 *   <li>{@link #GRAFT}: the lead's Trigger sets off the partner's Strike.</li>
 *   <li>{@link #ECHO}: the partner's Trigger sets off the lead's Strike.</li>
 *   <li>{@link #BARTER}: a gesture pays the partner's Fuel for the lead's Boon.</li>
 *   <li>{@link #ATTUNEMENT}: while the lead's State holds, the partner's Boon is granted on an interval.</li>
 *   <li>{@link #CONVERGENCE}: the lead's Trigger while the partner's State holds runs both Strikes.</li>
 * </ul>
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.Experimental
public enum Recipe {
  GRAFT(MutationPrice.GRAFT),
  ECHO(MutationPrice.GRAFT),
  BARTER(MutationPrice.BARTER),
  ATTUNEMENT(MutationPrice.ATTUNEMENT),
  CONVERGENCE(MutationPrice.CONVERGENCE);

  private final MutationPrice price;

  Recipe(MutationPrice price) {
    this.price = price;
  }

  /** The knowledge price of the adaptation this recipe builds; Graft and Echo share one. */
  public MutationPrice price() {
    return price;
  }
}
