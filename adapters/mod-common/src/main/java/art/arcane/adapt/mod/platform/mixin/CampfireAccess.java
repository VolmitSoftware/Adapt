package art.arcane.adapt.mod.platform.mixin;

import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CampfireBlockEntity.class)
public interface CampfireAccess {
    @Accessor("cookingProgress")
    int[] adapt$cookingProgress();

    @Accessor("cookingTime")
    int[] adapt$cookingTime();
}
