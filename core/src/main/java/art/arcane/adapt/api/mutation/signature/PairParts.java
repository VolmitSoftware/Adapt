package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import org.jetbrains.annotations.Nullable;

/** What the pair signatures read from their parent skills: a parent's upgrade. */
final class PairParts {
  private PairParts() {
  }

  /** The adaptation of {@code type} that skill {@code skillId} owns on this server, or null. */
  static <T> @Nullable T owned(String skillId, Class<T> type) {
    AdaptCore core = AdaptCore.current();
    Skill<?> skill = core == null || core.getAdaptServer() == null ? null
        : core.getAdaptServer().getSkillRegistry().getSkill(skillId);
    if (skill == null) {
      return null;
    }
    for (Adaptation<?> adaptation : Skill.ownedAdaptations(skill)) {
      if (type.isInstance(adaptation)) {
        return type.cast(adaptation);
      }
    }
    return null;
  }
}
