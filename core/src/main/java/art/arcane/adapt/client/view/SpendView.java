package art.arcane.adapt.client.view;

import java.util.Objects;

/**
 * One adaptation the player works with and what it costs them: the knowledge an unlearn would refund and the power slot
 * it takes. {@code skillId} is the skill page it sits under, a mutated skill's for its upgrades and kept originals.
 * Kept and lent originals cost nothing.
 */
public record SpendView(String skillId, String adaptationId, int level, int knowledge, int power, Source source) {
  public SpendView {
    Objects.requireNonNull(skillId, "skillId");
    Objects.requireNonNull(adaptationId, "adaptationId");
    Objects.requireNonNull(source, "source");
  }

  /** How the player holds the adaptation. */
  public enum Source {
    LEARNED,
    KEPT,
    LENT
  }
}
