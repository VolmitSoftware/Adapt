package art.arcane.adapt.mod.feature.ai;

import art.arcane.adapt.platform.entity.ai.AdaptGoal;
import art.arcane.adapt.platform.entity.ai.GoalFlag;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.Set;

/** Runs an {@link AdaptGoal} inside a vanilla goal selector. */
final class VanillaAdaptGoal extends Goal {
    private final AdaptGoal goal;

    VanillaAdaptGoal(AdaptGoal goal) {
        this.goal = goal;
        setFlags(flags(goal.flags()));
    }

    AdaptGoal adaptGoal() {
        return goal;
    }

    @Override
    public boolean canUse() {
        return goal.shouldActivate();
    }

    @Override
    public boolean canContinueToUse() {
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
    public boolean requiresUpdateEveryTick() {
        return goal.tickEveryTick();
    }

    @Override
    public String toString() {
        return goal.key().toString();
    }

    static EnumSet<Goal.Flag> flags(Set<GoalFlag> flags) {
        EnumSet<Goal.Flag> vanilla = EnumSet.noneOf(Goal.Flag.class);
        for (GoalFlag flag : flags) {
            vanilla.add(switch (flag) {
                case MOVE -> Goal.Flag.MOVE;
                case LOOK -> Goal.Flag.LOOK;
                case JUMP -> Goal.Flag.JUMP;
                case TARGET -> Goal.Flag.TARGET;
            });
        }
        return vanilla;
    }

    static Set<GoalFlag> flags(EnumSet<Goal.Flag> flags) {
        EnumSet<GoalFlag> adapt = EnumSet.noneOf(GoalFlag.class);
        for (Goal.Flag flag : flags) {
            switch (flag) {
                case MOVE -> adapt.add(GoalFlag.MOVE);
                case LOOK -> adapt.add(GoalFlag.LOOK);
                case JUMP -> adapt.add(GoalFlag.JUMP);
                case TARGET -> adapt.add(GoalFlag.TARGET);
                default -> {
                }
            }
        }
        return Set.copyOf(adapt);
    }
}
