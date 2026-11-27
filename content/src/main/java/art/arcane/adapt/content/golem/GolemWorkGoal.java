package art.arcane.adapt.content.golem;

import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.ai.AdaptGoal;
import art.arcane.adapt.platform.entity.ai.GoalFlag;
import art.arcane.adapt.platform.value.Id;

import java.util.Objects;
import java.util.Set;

final class GolemWorkGoal implements AdaptGoal {
  static final Id KEY = Id.of("adapt", "golem_work");

  private final MobRef golem;
  private final GolemPathing pathing;

  GolemWorkGoal(MobRef golem, GolemPathing pathing) {
    this.golem = Objects.requireNonNull(golem);
    this.pathing = Objects.requireNonNull(pathing);
  }

  @Override
  public Id key() {
    return KEY;
  }

  @Override
  public Set<GoalFlag> flags() {
    return Set.of(GoalFlag.MOVE);
  }

  @Override
  public boolean shouldActivate() {
    return pathing.hasDestination();
  }

  @Override
  public boolean shouldStayActive() {
    return pathing.hasDestination();
  }

  @Override
  public void start() {
    pathing.restart();
    pathing.tick();
  }

  @Override
  public void stop() {
    if (pathing.hasDestination()) {
      pathing.restart();
      golem.stopPathing();
    }
  }

  @Override
  public void tick() {
    pathing.tick();
  }
}
