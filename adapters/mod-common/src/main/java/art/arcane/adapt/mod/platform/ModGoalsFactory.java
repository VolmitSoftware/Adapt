package art.arcane.adapt.mod.platform;

import art.arcane.adapt.platform.entity.ai.MobGoalsRef;
import net.minecraft.world.entity.Mob;

public interface ModGoalsFactory {
    MobGoalsRef goals(Mob mob);
}
