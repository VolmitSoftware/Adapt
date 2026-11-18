package art.arcane.adapt.mod.feature.ai;

import art.arcane.adapt.mod.feature.mixin.MobSelectorsAccess;
import art.arcane.adapt.platform.entity.ai.AdaptGoal;
import art.arcane.adapt.platform.entity.ai.GoalEntry;
import art.arcane.adapt.platform.entity.ai.GoalFlag;
import art.arcane.adapt.platform.entity.ai.MobGoalsRef;
import art.arcane.adapt.platform.value.Id;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Both vanilla selectors of one mob behind {@link MobGoalsRef}. */
final class ModMobGoals implements MobGoalsRef {
    private static final Comparator<GoalEntry> PRIORITY = Comparator.comparingInt(GoalEntry::priority);

    private final Mob mob;

    ModMobGoals(Mob mob) {
        this.mob = mob;
    }

    @Override
    public List<GoalEntry> all() {
        List<GoalEntry> entries = new ArrayList<>();
        collect(goals(), false, entries);
        collect(targets(), true, entries);
        entries.sort(PRIORITY);
        return entries;
    }

    @Override
    public List<GoalEntry> withKey(Id key) {
        List<GoalEntry> matching = new ArrayList<>();
        for (GoalEntry entry : all()) {
            if (entry.key().equals(key)) {
                matching.add(entry);
            }
        }
        return matching;
    }

    @Override
    public GoalEntry add(int priority, AdaptGoal goal) {
        boolean target = goal.flags().contains(GoalFlag.TARGET);
        VanillaAdaptGoal vanilla = new VanillaAdaptGoal(goal);
        selector(target).addGoal(priority, vanilla);
        return new ModGoalEntry(goal.key(), priority, vanilla, target);
    }

    @Override
    public void restore(GoalEntry entry) {
        restore(entry, entry.priority());
    }

    @Override
    public void restore(GoalEntry entry, int priority) {
        if (!(entry instanceof ModGoalEntry modEntry)) {
            throw new IllegalArgumentException("Not a modded goal entry: " + entry);
        }
        GoalSelector selector = selector(modEntry.target());
        for (WrappedGoal wrapped : selector.getAvailableGoals()) {
            if (wrapped.getGoal() == modEntry.goal()) {
                return;
            }
        }
        selector.addGoal(priority, modEntry.goal());
    }

    @Override
    public void remove(GoalEntry entry) {
        if (entry instanceof ModGoalEntry modEntry) {
            selector(modEntry.target()).removeGoal(modEntry.goal());
        }
    }

    @Override
    public List<GoalEntry> removeAll(Id key) {
        List<GoalEntry> removed = withKey(key);
        for (GoalEntry entry : removed) {
            remove(entry);
        }
        return removed;
    }

    @Override
    public boolean has(Id key) {
        return !withKey(key).isEmpty();
    }

    private void collect(GoalSelector selector, boolean target, List<GoalEntry> into) {
        for (WrappedGoal wrapped : List.copyOf(selector.getAvailableGoals())) {
            into.add(ModGoalEntry.of(wrapped.getPriority(), wrapped.getGoal(), target));
        }
    }

    private GoalSelector selector(boolean target) {
        return target ? targets() : goals();
    }

    private GoalSelector goals() {
        return ((MobSelectorsAccess) mob).adapt$goalSelector();
    }

    private GoalSelector targets() {
        return ((MobSelectorsAccess) mob).adapt$targetSelector();
    }
}
