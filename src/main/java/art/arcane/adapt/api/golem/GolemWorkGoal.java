package art.arcane.adapt.api.golem;

import art.arcane.adapt.util.common.compat.PaperCompat;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;

import java.util.EnumSet;
import java.util.Objects;

final class GolemWorkGoal implements Goal<Mob> {
  static final GoalKey<Mob> KEY = GoalKey.of(Mob.class, new NamespacedKey("adapt", "golem_work"));

  private final Mob golem;
  private final GolemPathing pathing;

  GolemWorkGoal(Mob golem, GolemPathing pathing) {
    this.golem = Objects.requireNonNull(golem);
    this.pathing = Objects.requireNonNull(pathing);
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
      PaperCompat.stopPathfinding(golem);
    }
  }

  @Override
  public void tick() {
    pathing.tick();
  }

  @Override
  public GoalKey<Mob> getKey() {
    return KEY;
  }

  @Override
  public EnumSet<GoalType> getTypes() {
    return EnumSet.of(GoalType.MOVE);
  }
}
