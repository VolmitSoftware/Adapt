package art.arcane.adapt.api.ability;

import org.jetbrains.annotations.ApiStatus;

/**
 * How charging one ability cost ended; {@link #allowed()} tells whether the ability may go ahead.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum AbilityOutcome {
  /** The server config turns the ability hooks off; only the default cost was taken. */
  DISABLED(true),
  /** No provider waived or took the price, so the default cost was taken. */
  ALLOWED_DEFAULT(true),
  /** A provider waived the cost and nobody reserved a price; nothing was taken. */
  ALLOWED_WAIVED(true),
  /** At least one provider reserved a price in place of the default cost. */
  ALLOWED_CHARGED(true),
  /** A provider failed and the server config lets cost failures through. */
  ALLOWED_PROVIDER_FAILED(true),
  DENIED_BY_LISTENER(false),
  /** A provider quoted {@link AbilityQuoteStatus#DENIED}. */
  DENIED_BY_PROVIDER(false),
  /**
   * The player cannot pay: a provider quoted {@link AbilityQuoteStatus#INSUFFICIENT}, a reservation failed or the
   * default cost could not be taken.
   */
  DENIED_INSUFFICIENT(false),
  /** A provider failed and the server config makes cost failures deny. */
  DENIED_PROVIDER_FAILED(false),
  /** A cost provider charged another cost from inside its own callback. */
  DENIED_REENTRANT(false);

  private final boolean allowed;

  AbilityOutcome(boolean allowed) {
    this.allowed = allowed;
  }

  /** Whether the ability may go ahead after this outcome. */
  public boolean allowed() {
    return allowed;
  }
}
