package art.arcane.adapt.mod.feature.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Interaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Interaction.class)
public interface InteractionDataAccess {
    @Accessor("DATA_WIDTH_ID")
    static EntityDataAccessor<Float> adapt$width() {
        throw new AssertionError("Mixin accessor was not applied");
    }

    @Accessor("DATA_HEIGHT_ID")
    static EntityDataAccessor<Float> adapt$height() {
        throw new AssertionError("Mixin accessor was not applied");
    }
}
