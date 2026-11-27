package art.arcane.adapt.content.golem;

import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.ai.AdaptGoal;
import art.arcane.adapt.platform.entity.ai.GoalFlag;
import art.arcane.adapt.platform.value.Id;

import java.util.Objects;
import java.util.Set;

final class GolemTargetGoal implements AdaptGoal {
  static final Id KEY = Id.of("adapt", "golem_target");

  private final MobRef golem;
  private LivingRef target;
  private LivingRef applied;

  GolemTargetGoal(MobRef golem) {
    this.golem = Objects.requireNonNull(golem);
  }

  void assign(LivingRef next) {
    target = next;
  }

  @Override
  public Id key() {
    return KEY;
  }

  @Override
  public Set<GoalFlag> flags() {
    return Set.of(GoalFlag.TARGET);
  }

  @Override
  public boolean shouldActivate() {
    return isLive(target);
  }

  @Override
  public boolean shouldStayActive() {
    return isLive(target);
  }

  @Override
  public void start() {
    apply();
  }

  @Override
  public void tick() {
    if (!isSame(golem.target(), target)) {
      apply();
    }
  }

  @Override
  public void stop() {
    if (applied != null && isSame(golem.target(), applied)) {
      golem.setTarget(null);
    }
    applied = null;
  }

  private void apply() {
    if (!isLive(target)) {
      return;
    }
    applied = target;
    golem.setTarget(target);
  }

  private static boolean isLive(LivingRef entity) {
    return entity != null && entity.isValid() && !entity.isDead();
  }

  private static boolean isSame(LivingRef current, LivingRef expected) {
    return current != null && expected != null && current.uuid().equals(expected.uuid());
  }
}
