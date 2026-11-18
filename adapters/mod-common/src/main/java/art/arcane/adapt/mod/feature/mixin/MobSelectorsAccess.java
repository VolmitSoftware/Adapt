package art.arcane.adapt.mod.feature.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Mob.class)
public interface MobSelectorsAccess {
    @Accessor("goalSelector")
    GoalSelector adapt$goalSelector();

    @Accessor("targetSelector")
    GoalSelector adapt$targetSelector();
}
