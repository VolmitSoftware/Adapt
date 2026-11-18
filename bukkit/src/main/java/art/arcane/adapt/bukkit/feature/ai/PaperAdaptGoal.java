package art.arcane.adapt.bukkit.feature.ai;

import art.arcane.adapt.platform.entity.ai.AdaptGoal;
import art.arcane.adapt.platform.entity.ai.GoalFlag;
import art.arcane.adapt.platform.value.Id;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;

import java.util.EnumSet;
import java.util.Set;

/** Runs an {@link AdaptGoal} as a Paper goal keyed {@code GoalKey.of(Mob.class, key)}, as golemancy's goals are. */
final class PaperAdaptGoal implements Goal<Mob> {
  private final AdaptGoal goal;
  private final GoalKey<Mob> key;
  private final EnumSet<GoalType> types;

  PaperAdaptGoal(AdaptGoal goal) {
    this.goal = goal;
    this.key = goalKey(goal.key());
    this.types = types(goal.flags());
  }

  AdaptGoal adaptGoal() {
    return goal;
  }

  @Override
  public boolean shouldActivate() {
    return goal.shouldActivate();
  }

  @Override
  public boolean shouldStayActive() {
    return goal.shouldStayActive();
  }

  @Override
  public void start() {
    goal.start();
  }

  @Override
  public void stop() {
    goal.stop();
  }

  @Override
  public void tick() {
    goal.tick();
  }

  @Override
  public GoalKey<Mob> getKey() {
    return key;
  }

  @Override
  public EnumSet<GoalType> getTypes() {
    return types.clone();
  }

  static GoalKey<Mob> goalKey(Id id) {
    return GoalKey.of(Mob.class, new NamespacedKey(id.namespace(), id.path()));
  }

  static Id id(GoalKey<?> key) {
    NamespacedKey namespaced = key.getNamespacedKey();
    return Id.of(namespaced.getNamespace(), namespaced.getKey());
  }

  static EnumSet<GoalType> types(Set<GoalFlag> flags) {
    EnumSet<GoalType> types = EnumSet.noneOf(GoalType.class);
    for (GoalFlag flag : flags) {
      types.add(switch (flag) {
        case MOVE -> GoalType.MOVE;
        case LOOK -> GoalType.LOOK;
        case JUMP -> GoalType.JUMP;
        case TARGET -> GoalType.TARGET;
      });
    }
    return types;
  }

  static Set<GoalFlag> flags(Set<GoalType> types) {
    EnumSet<GoalFlag> flags = EnumSet.noneOf(GoalFlag.class);
    for (GoalType type : types) {
      switch (type) {
        case MOVE -> flags.add(GoalFlag.MOVE);
        case LOOK -> flags.add(GoalFlag.LOOK);
        case JUMP -> flags.add(GoalFlag.JUMP);
        case TARGET -> flags.add(GoalFlag.TARGET);
        default -> {
        }
      }
    }
    return flags;
  }
}
