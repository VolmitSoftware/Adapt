package art.arcane.adapt.api.ability;

import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** A use policy's answer to {@link AbilityUsePolicy#evaluate(AbilityContext)}. Made through the factories. */
@ApiStatus.AvailableSince("1.0.0")
public final class AbilityUseDecision {
  private static final AbilityUseDecision ALLOW = new AbilityUseDecision(AbilityUseStatus.ALLOW, "");

  private final AbilityUseStatus status;
  private final String reason;

  private AbilityUseDecision(AbilityUseStatus status, String reason) {
    this.status = Objects.requireNonNull(status, "status");
    this.reason = AbilityText.sanitize(reason);
  }

  /** Lets the use go ahead, as far as this policy is concerned. */
  public static AbilityUseDecision allow() {
    return ALLOW;
  }

  /** Refuses the use; no later policy is asked. */
  public static AbilityUseDecision deny(String reason) {
    return new AbilityUseDecision(AbilityUseStatus.DENY, reason);
  }

  /** Whether the use may go ahead. */
  public AbilityUseStatus status() {
    return status;
  }

  /** Why the use was refused, cut to 128 characters; empty when allowed. */
  public String reason() {
    return reason;
  }

  /** Whether the status is {@link AbilityUseStatus#ALLOW}. */
  public boolean allowed() {
    return status == AbilityUseStatus.ALLOW;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof AbilityUseDecision decision && status == decision.status && reason.equals(decision.reason);
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, reason);
  }

  @Override
  public String toString() {
    return "AbilityUseDecision[status=" + status + ", reason=" + reason + "]";
  }
}
