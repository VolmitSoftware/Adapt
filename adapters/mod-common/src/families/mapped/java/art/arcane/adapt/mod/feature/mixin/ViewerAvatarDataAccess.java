package art.arcane.adapt.mod.feature.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Player.class)
public interface ViewerAvatarDataAccess {
    @Accessor("DATA_PLAYER_MODE_CUSTOMISATION")
    static EntityDataAccessor<Byte> adapt$skinLayers() {
        throw new AssertionError("Mixin accessor was not applied");
    }
}
