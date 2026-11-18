package art.arcane.adapt.mod.feature.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface ViewerEntityDataAccess {
    @Accessor("DATA_SHARED_FLAGS_ID")
    static EntityDataAccessor<Byte> adapt$sharedFlags() {
        throw new AssertionError("Mixin accessor was not applied");
    }
}
