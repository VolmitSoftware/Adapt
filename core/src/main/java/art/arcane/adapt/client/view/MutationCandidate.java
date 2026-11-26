package art.arcane.adapt.client.view;

import java.util.Objects;

/** A skill the spread offers as a partner, its level, and why it cannot join when it cannot. */
public record MutationCandidate(String skillId, int level, State state, String reason) {
  public MutationCandidate {
    Objects.requireNonNull(skillId, "skillId");
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(reason, "reason");
  }

  public boolean eligible() {
    return state == State.ELIGIBLE;
  }

  /** Whether the skill can join the lead now, or the rule that stops it. */
  public enum State {
    ELIGIBLE,
    LEVEL_TOO_LOW,
    ALREADY_MUTATED,
    LIMIT_REACHED
  }
}
