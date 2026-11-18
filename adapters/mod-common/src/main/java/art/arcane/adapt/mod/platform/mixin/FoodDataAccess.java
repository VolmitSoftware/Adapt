package art.arcane.adapt.mod.platform.mixin;

import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FoodData.class)
public interface FoodDataAccess {
    @Accessor("exhaustionLevel")
    float adapt$exhaustion();

    @Accessor("exhaustionLevel")
    void adapt$setExhaustion(float exhaustion);
}
