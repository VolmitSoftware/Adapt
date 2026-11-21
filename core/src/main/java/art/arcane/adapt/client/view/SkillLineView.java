package art.arcane.adapt.client.view;

import java.util.Objects;

/** A player's progress in one skill. {@code progress} is 0..1 toward the next level; {@code xpToNext} is what remains. */
public record SkillLineView(String skillId, double xp, int level, double progress, double xpToNext, long knowledge) {
  public SkillLineView {
    Objects.requireNonNull(skillId, "skillId");
  }
}
