package art.arcane.adapt.client.protocol;

import java.util.Objects;

/** A designed client screen. */
public sealed interface Route {
  record Skills() implements Route {
  }

  record Skill(String skillId) implements Route {
    public Skill {
      Objects.requireNonNull(skillId, "skillId");
    }
  }

  record Adaptation(String adaptationId) implements Route {
    public Adaptation {
      Objects.requireNonNull(adaptationId, "adaptationId");
    }
  }

  /** The mutation spread led by {@code skillId}; only under {@link ClientProtocol#MUTATE_SPREAD}. */
  record Mutate(String skillId) implements Route {
    public Mutate {
      Objects.requireNonNull(skillId, "skillId");
    }
  }
}
