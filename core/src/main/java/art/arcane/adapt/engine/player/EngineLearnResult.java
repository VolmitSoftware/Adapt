package art.arcane.adapt.engine.player;

import art.arcane.adapt.api.player.LearnResult;

import java.util.Objects;

public record EngineLearnResult(Status status, int fromLevel, int toLevel, long knowledge) implements LearnResult {
  public EngineLearnResult {
    Objects.requireNonNull(status, "status");
  }
}
