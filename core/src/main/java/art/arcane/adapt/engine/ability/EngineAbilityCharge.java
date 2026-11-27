package art.arcane.adapt.engine.ability;

import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityOutcome;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record EngineAbilityCharge(UUID activationId, AbilityOutcome outcome, boolean defaultCostSuppressed,
                                  String reason, String providerId, List<String> chargedProviderIds)
    implements AbilityCharge {
  public EngineAbilityCharge {
    Objects.requireNonNull(activationId, "activationId");
    Objects.requireNonNull(outcome, "outcome");
    reason = AbilityStrings.sanitize(reason);
    providerId = providerId == null ? "" : providerId;
    chargedProviderIds = chargedProviderIds == null ? List.of() : List.copyOf(chargedProviderIds);
  }
}
