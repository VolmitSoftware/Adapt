package art.arcane.adapt.api.ability;

public enum AbilityOutcome {
  DISABLED(true),
  ALLOWED_DEFAULT(true),
  ALLOWED_WAIVED(true),
  ALLOWED_CHARGED(true),
  ALLOWED_PROVIDER_FAILED(true),
  DENIED_BY_LISTENER(false),
  DENIED_BY_PROVIDER(false),
  DENIED_INSUFFICIENT(false),
  DENIED_PROVIDER_FAILED(false),
  DENIED_REENTRANT(false);

  private final boolean allowed;

  AbilityOutcome(boolean allowed) {
    this.allowed = allowed;
  }

  public boolean allowed() {
    return allowed;
  }
}
