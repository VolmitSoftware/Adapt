package art.arcane.adapt.engine.player;

import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.player.MutationResult;

import java.util.Objects;
import java.util.Optional;

public record EngineMutationResult(Status status, Optional<SkillPair> pair) implements MutationResult {
  public EngineMutationResult {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(pair, "pair");
  }
}
