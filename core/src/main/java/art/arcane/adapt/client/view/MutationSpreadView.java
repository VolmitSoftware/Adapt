package art.arcane.adapt.client.view;

import java.util.List;
import java.util.Objects;

/**
 * Everything the mutation spread shows for one lead and an optional partner, computed by the server's preview.
 * {@code name}, {@code summary} and {@code mutated} are empty until a partner is chosen; {@code summary} is the
 * resulting level and knowledge. {@code mutatedSkillId} is set only on the answer to a confirm that went through.
 */
public record MutationSpreadView(String lead, String partner, String name, String summary, List<MutationEntry> mutated,
                                 MutationSide leadSide, MutationSide partnerSide, List<MutationCandidate> candidates,
                                 MutationWords words, String mutatedSkillId) {
  public MutationSpreadView {
    Objects.requireNonNull(lead, "lead");
    Objects.requireNonNull(partner, "partner");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(summary, "summary");
    Objects.requireNonNull(leadSide, "leadSide");
    Objects.requireNonNull(partnerSide, "partnerSide");
    Objects.requireNonNull(words, "words");
    Objects.requireNonNull(mutatedSkillId, "mutatedSkillId");
    mutated = List.copyOf(mutated);
    candidates = List.copyOf(candidates);
  }

  public boolean mutatedNow() {
    return !mutatedSkillId.isEmpty();
  }

  /** The candidate entry for {@code skillId}, or null. */
  public MutationCandidate candidate(String skillId) {
    for (MutationCandidate candidate : candidates) {
      if (candidate.skillId().equals(skillId)) {
        return candidate;
      }
    }
    return null;
  }
}
