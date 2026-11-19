package art.arcane.adapt.mod.platform.mixin;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.animal.MushroomCow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A 1.20.1 mooshroom holds at most one stew effect, null when it has none. */
@Mixin(MushroomCow.class)
public interface MushroomCowAccess {
    @Accessor("effect")
    MobEffect adapt$stewEffects();
}
