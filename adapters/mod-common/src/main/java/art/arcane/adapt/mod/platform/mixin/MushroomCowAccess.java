package art.arcane.adapt.mod.platform.mixin;

import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MushroomCow.class)
public interface MushroomCowAccess {
    @Accessor("stewEffects")
    SuspiciousStewEffects adapt$stewEffects();
}
