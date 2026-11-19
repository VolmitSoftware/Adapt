package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.player.PlayerGapHooks;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/** Chat broadcasts, movement key packets and recipe book clicks. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ChatInputRecipeEvents {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;)V", at = @At("HEAD"), cancellable = true)
    private void adapt$chatting(PlayerChatMessage message, CallbackInfo callback) {
        if (PlayerGapHooks.chatting(player, message)) {
            callback.cancel();
        }
    }

    @Inject(method = "handlePlayerInput(Lnet/minecraft/network/protocol/game/ServerboundPlayerInputPacket;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;setPlayerInput(FFZZ)V"))
    private void adapt$input(ServerboundPlayerInputPacket packet, CallbackInfo callback) {
        PlayerGapHooks.input(player, packet);
    }

    @ModifyExpressionValue(method = "handlePlaceRecipe(Lnet/minecraft/network/protocol/game/ServerboundPlaceRecipePacket;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/crafting/RecipeManager;byKey(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;"))
    private Optional<?> adapt$recipeClicked(Optional<?> recipe, ServerboundPlaceRecipePacket packet) {
        if (recipe.isPresent()) {
            PlayerGapHooks.recipeClicked(player, packet.getRecipe(), packet.isShiftDown());
        }
        return recipe;
    }
}
