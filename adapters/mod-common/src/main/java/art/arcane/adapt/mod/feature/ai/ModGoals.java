package art.arcane.adapt.mod.feature.ai;

import art.arcane.adapt.mod.platform.ModGoalsFactory;
import art.arcane.adapt.platform.entity.ai.MobGoalsRef;
import net.minecraft.world.entity.Mob;

public final class ModGoals implements ModGoalsFactory {
    @Override
    public MobGoalsRef goals(Mob mob) {
        return new ModMobGoals(mob);
    }
}
