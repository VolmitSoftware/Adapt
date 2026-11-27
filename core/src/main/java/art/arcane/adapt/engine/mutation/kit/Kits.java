package art.arcane.adapt.engine.mutation.kit;

import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.engine.mutation.MutatedSkill;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** The mutation kit of any skill: the one it registered, or the one generated for a skill that registered none. */
public final class Kits {
  private static final Map<Skill<?>, MutationKit> GENERATED = Collections.synchronizedMap(new WeakHashMap<>());

  private Kits() {
  }

  public static MutationKit of(Skill<?> skill) {
    return skill.getMutationKit().orElseGet(() -> GENERATED.computeIfAbsent(skill, DefaultKit::of));
  }

  /** Whether the skill can be either side of a mutation on this server: enabled, with adaptations, not mutated itself. */
  public static boolean mergeable(Skill<?> skill) {
    return !(skill instanceof MutatedSkill) && skill.isEnabled() && !skill.getAdaptations().isEmpty();
  }
}
