package art.arcane.adapt.api.ability.internal;

import java.util.Objects;

public record AbilityDenial(String abilityId, String providerId, String reason, long atMillis) {
  public AbilityDenial {
    Objects.requireNonNull(abilityId, "abilityId");
    providerId = providerId == null ? "" : providerId;
    reason = reason == null ? "" : reason;
  }
}
