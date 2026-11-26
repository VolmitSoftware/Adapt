package art.arcane.adapt.client.view;

import java.util.List;
import java.util.Objects;

/**
 * How one skill earns XP, in the viewer's language. A plain skill has one section of its own; a mutated skill names its
 * two parents in {@code parents}, lead first, and has one section per parent followed by the mutation's rules.
 */
public record XpGuideView(String skillId, List<String> parents, List<Section> sections) {
  public XpGuideView {
    Objects.requireNonNull(skillId, "skillId");
    parents = List.copyOf(parents);
    sections = List.copyOf(sections);
  }

  public boolean mutated() {
    return !parents.isEmpty();
  }

  /** Lines under a title; {@code skillId} names the skill they describe, or is empty for the mutation's rules. */
  public record Section(String skillId, String title, List<String> lines) {
    public Section {
      Objects.requireNonNull(skillId, "skillId");
      Objects.requireNonNull(title, "title");
      lines = List.copyOf(lines);
    }
  }
}
