package art.arcane.adapt.mod.platform.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftServer.class)
public interface MinecraftServerStorageAccess {
    @Accessor("playerDataStorage")
    PlayerDataStorage adapt$playerDataStorage();
}
