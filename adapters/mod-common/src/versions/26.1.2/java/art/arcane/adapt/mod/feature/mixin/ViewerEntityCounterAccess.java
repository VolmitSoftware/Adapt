package art.arcane.adapt.mod.feature.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.atomic.AtomicInteger;

@Mixin(Entity.class)
public interface ViewerEntityCounterAccess {
    @Accessor("ENTITY_COUNTER")
    static AtomicInteger adapt$entityCounter() {
        throw new AssertionError("Mixin accessor was not applied");
    }
}
