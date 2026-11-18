package art.arcane.adapt.api.ability;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AbilityCharge(UUID activationId, AbilityOutcome outcome, boolean defaultCostSuppressed, String reason,
                            String providerId, List<String> chargedProviderIds) {
  public AbilityCharge {
    Objects.requireNonNull(activationId, "activationId");
    Objects.requireNonNull(outcome, "outcome");
    reason = AbilityText.sanitize(reason);
    providerId = providerId == null ? "" : providerId;
    chargedProviderIds = chargedProviderIds == null ? List.of() : List.copyOf(chargedProviderIds);
  }

  public boolean allowed() {
    return outcome.allowed();
  }
}
