package art.arcane.adapt.mod.platform.mixin;

import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Villager.class)
public interface VillagerAccess {
    @Invoker("setUnhappy")
    void adapt$setUnhappy();
}
