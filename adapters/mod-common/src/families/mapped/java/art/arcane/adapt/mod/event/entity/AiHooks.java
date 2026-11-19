package art.arcane.adapt.mod.event.entity;

import art.arcane.adapt.mod.event.CauseStack;
import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.mod.event.ModEvents;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.event.entity.EntityPathfindEvent;
import art.arcane.adapt.platform.event.entity.ItemTransportingEntityValidateTargetEvent;
import art.arcane.adapt.platform.event.entity.PiglinBarterEvent;
import art.arcane.adapt.platform.item.ItemRef;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Mob pathfinding, copper golem container targets, piglin bartering, and attack targets a brain forgets with time. */
public final class AiHooks {
    private static final CauseStack<Entity> PATH_TARGETS = new CauseStack<>();

    private AiHooks() {
    }

    public static void pathingTo(Entity target) {
        PATH_TARGETS.push(target, false);
    }

    public static void pathed() {
        PATH_TARGETS.pop();
    }

    /** Before vanilla searches a path. True when a handler cancelled; no path is returned then. */
    public static boolean pathfinding(Mob mob, Set<BlockPos> targets) {
        if (!(mob.level() instanceof ServerLevel level) || !ModEvents.listening(EntityPathfindEvent.class)) {
            return false;
        }
        Iterator<BlockPos> iterator = targets.iterator();
        if (!iterator.hasNext()) {
            return false;
        }
        BlockPos pos = iterator.next();
        Entity target = PATH_TARGETS.current();
        EntityPathfindEvent event = new EntityPathfindEvent(ModRefs.entity(mob),
            ModRefs.loc(level, new Vec3(pos.getX(), pos.getY(), pos.getZ()), 0.0F, 0.0F), target == null ? null : ModRefs.entity(target));
        return ModEvents.post(event).isCancelled();
    }

    /** A container vanilla accepted as a copper golem transport target. False when a handler disallowed it. */
    public static boolean transportTarget(Mob body, BlockPos pos) {
        if (!(body.level() instanceof ServerLevel level) || !ModEvents.listening(ItemTransportingEntityValidateTargetEvent.class)) {
            return true;
        }
        return ModEvents.post(new ItemTransportingEntityValidateTargetEvent(ModRefs.entity(body), ModRefs.block(level, pos), true)).allowed();
    }

    /** The items a piglin hands back for {@code input}: the handlers' list, or none when cancelled. */
    public static List<ItemStack> bartering(Mob piglin, ItemStack input, List<ItemStack> outcome) {
        if (!(piglin.level() instanceof ServerLevel) ||!ModEvents.listening(PiglinBarterEvent.class)) {
            return outcome;
        }
        List<ItemRef> refs = new ArrayList<>(outcome.size());
        for (ItemStack stack : outcome) {
            refs.add(ModRefs.item(stack));
        }
        PiglinBarterEvent event = ModEvents.post(new PiglinBarterEvent(ModRefs.entity(piglin), ModRefs.item(input.copy()), refs));
        if (event.isCancelled()) {
            return new ArrayList<>();
        }
        List<ItemStack> result = new ArrayList<>(event.outcome().size());
        for (ItemRef ref : event.outcome()) {
            if (ref == null) {
                continue;
            }
            ItemStack stack = ModRefs.item(ref);
            if (!stack.isEmpty()) {
                result.add(stack);
            }
        }
        return result;
    }

    /**
     * Before a brain ticks its memories: an attack target whose time to live ran out is about to be
     * forgotten. A cancelled event keeps it without a time limit; a new target replaces it.
     */
    public static void brainExpiring(LivingEntity owner, Brain<?> brain, ExpirableValue<LivingEntity> memory) {
        if (owner == null || memory == null || !memory.canExpire() || !memory.hasExpired()) {
            return;
        }
        LivingEntity current = memory.getValue();
        Object result = CombatHooks.brainTargeting(owner, current, null);
        if (result == CombatHooks.KEEP_TARGET) {
            brain.setMemory(MemoryModuleType.ATTACK_TARGET, current);
        } else if (result instanceof LivingEntity target) {
            brain.setMemory(MemoryModuleType.ATTACK_TARGET, target);
        }
    }
}
