package art.arcane.adapt.mod.feature.ai;

import art.arcane.adapt.platform.entity.ai.GoalEntry;
import art.arcane.adapt.platform.entity.ai.GoalFlag;
import art.arcane.adapt.platform.value.Id;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.Set;

/** A goal on one of a mob's two selectors; equal entries wrap the same goal instance at the same priority. */
record ModGoalEntry(Id key, int priority, Goal goal, boolean target) implements GoalEntry {
    static ModGoalEntry of(int priority, Goal goal, boolean target) {
        Id key = goal instanceof VanillaAdaptGoal custom ? custom.adaptGoal().key() : PaperGoalKeys.key(goal.getClass());
        return new ModGoalEntry(key, priority, goal, target);
    }

    @Override
    public Set<GoalFlag> flags() {
        return VanillaAdaptGoal.flags(goal.getFlags());
    }

    @Override
    public boolean isCustom() {
        return goal instanceof VanillaAdaptGoal;
    }

    @Override
    public Object unwrap() {
        return goal;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ModGoalEntry entry && entry.goal == goal && entry.priority == priority && entry.target == target;
    }

    @Override
    public int hashCode() {
        return (System.identityHashCode(goal) * 31 + priority) * 2 + (target ? 1 : 0);
    }
}
