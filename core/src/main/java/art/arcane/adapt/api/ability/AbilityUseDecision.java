package art.arcane.adapt.api.ability;

import java.util.Objects;

public record AbilityUseDecision(AbilityUseStatus status, String reason) {
  private static final AbilityUseDecision ALLOW = new AbilityUseDecision(AbilityUseStatus.ALLOW, "");

  public AbilityUseDecision {
    Objects.requireNonNull(status, "status");
    reason = AbilityText.sanitize(reason);
  }

  public static AbilityUseDecision allow() {
    return ALLOW;
  }

  public static AbilityUseDecision deny(String reason) {
    return new AbilityUseDecision(AbilityUseStatus.DENY, reason);
  }

  public boolean allowed() {
    return status == AbilityUseStatus.ALLOW;
  }
}
