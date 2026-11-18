package art.arcane.adapt.clientqa.mixin;

import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("profileFuture")
    CompletableFuture<ProfileResult> profileFuture();
}
