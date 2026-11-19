package art.arcane.adapt.gameplay.mod.mixin;

import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Fishing hook timers the fixture reports; their names are obfuscated at runtime on these versions. */
@Mixin(FishingHook.class)
public interface FishingHookAccess {
    @Accessor("timeUntilLured")
    int adaptqa$timeUntilLured();

    @Accessor("timeUntilHooked")
    int adaptqa$timeUntilHooked();
}
