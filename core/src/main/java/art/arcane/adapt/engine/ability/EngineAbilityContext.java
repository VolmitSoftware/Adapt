package art.arcane.adapt.engine.ability;

import art.arcane.adapt.api.ability.AbilityContext;
import art.arcane.adapt.api.ability.AbilityPhase;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record EngineAbilityContext(UUID activationId, Id ability, Id skill, int level, AbilityPhase phase,
                                   PlayerRef player, Optional<Loc> origin) implements AbilityContext {
  public EngineAbilityContext {
    Objects.requireNonNull(activationId, "activationId");
    Objects.requireNonNull(ability, "ability");
    Objects.requireNonNull(skill, "skill");
    Objects.requireNonNull(phase, "phase");
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(origin, "origin");
    if (level < 0) {
      throw new IllegalArgumentException("an ability context level must not be negative");
    }
  }
}
