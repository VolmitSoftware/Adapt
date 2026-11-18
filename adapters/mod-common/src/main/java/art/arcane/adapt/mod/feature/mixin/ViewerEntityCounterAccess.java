package art.arcane.adapt.mod.feature.mixin;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.atomic.AtomicInteger;

@Mixin(ServerLevel.class)
public interface ViewerEntityCounterAccess {
    @Accessor("ENTITY_COUNTER")
    static AtomicInteger adapt$entityCounter() {
        throw new AssertionError("Mixin accessor was not applied");
    }
}
