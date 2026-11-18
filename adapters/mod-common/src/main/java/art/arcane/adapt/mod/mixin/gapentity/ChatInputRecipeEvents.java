package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.player.PlayerGapHooks;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Chat broadcasts, movement key packets and recipe book clicks. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ChatInputRecipeEvents {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "broadcastChatMessage", at = @At("HEAD"), cancellable = true)
    private void adapt$chatting(PlayerChatMessage message, CallbackInfo callback) {
        if (PlayerGapHooks.chatting(player, message)) {
            callback.cancel();
        }
    }

    @Inject(method = "handlePlayerInput", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;setLastClientInput(Lnet/minecraft/world/entity/player/Input;)V"))
    private void adapt$input(ServerboundPlayerInputPacket packet, CallbackInfo callback) {
        PlayerGapHooks.input(player, packet.input());
    }

    @ModifyExpressionValue(method = "handlePlaceRecipe", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRecipeFromDisplay(Lnet/minecraft/world/item/crafting/display/RecipeDisplayId;)Lnet/minecraft/world/item/crafting/RecipeManager$ServerDisplayInfo;"))
    private RecipeManager.ServerDisplayInfo adapt$recipeClicked(RecipeManager.ServerDisplayInfo display, ServerboundPlaceRecipePacket packet) {
        PlayerGapHooks.recipeClicked(player, display, packet.useMaxItems());
        return display;
    }
}
