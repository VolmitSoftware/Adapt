package art.arcane.adapt.mod.platform.mixin;

import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ArmorStand.class)
public interface ArmorStandAccess {
    @Invoker("setSmall")
    void adapt$setSmall(boolean small);

    @Invoker("setMarker")
    void adapt$setMarker(boolean marker);
}
