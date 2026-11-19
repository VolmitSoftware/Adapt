package art.arcane.adapt.mod.platform.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Biome.class)
public interface BiomeAccess {
    @Invoker("getTemperature")
    float adapt$temperature(BlockPos pos);
}
