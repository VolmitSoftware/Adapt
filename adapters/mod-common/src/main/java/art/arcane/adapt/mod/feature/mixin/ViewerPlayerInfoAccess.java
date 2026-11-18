package art.arcane.adapt.mod.feature.mixin;

import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ClientboundPlayerInfoUpdatePacket.class)
public interface ViewerPlayerInfoAccess {
    @Mutable
    @Accessor("entries")
    void adapt$entries(List<ClientboundPlayerInfoUpdatePacket.Entry> entries);
}
