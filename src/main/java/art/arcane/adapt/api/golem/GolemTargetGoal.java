package art.arcane.adapt.api.golem;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

import java.util.EnumSet;
import java.util.Objects;

final class GolemTargetGoal implements Goal<Mob> {
  static final GoalKey<Mob> KEY = GoalKey.of(Mob.class, new NamespacedKey("adapt", "golem_target"));

  private final Mob golem;
  private LivingEntity target;
  private LivingEntity applied;

  GolemTargetGoal(Mob golem) {
    this.golem = Objects.requireNonNull(golem);
  }

  void assign(LivingEntity next) {
    target = next;
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
    if (!isSame(golem.getTarget(), target)) {
      apply();
    }
  }

  @Override
  public void stop() {
    if (applied != null && isSame(golem.getTarget(), applied)) {
      golem.setTarget(null);
    }
    applied = null;
  }

  @Override
  public GoalKey<Mob> getKey() {
    return KEY;
  }

  @Override
  public EnumSet<GoalType> getTypes() {
    return EnumSet.of(GoalType.TARGET);
  }

  private void apply() {
    if (!isLive(target)) {
      return;
    }
    applied = target;
    golem.setTarget(target);
  }

  private static boolean isLive(LivingEntity entity) {
    return entity != null && entity.isValid() && !entity.isDead();
  }

  private static boolean isSame(LivingEntity current, LivingEntity expected) {
    return current != null && expected != null && current.getUniqueId().equals(expected.getUniqueId());
  }
}
