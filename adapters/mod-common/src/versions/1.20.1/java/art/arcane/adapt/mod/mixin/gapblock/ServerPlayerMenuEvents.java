package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.inventory.ContainerHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player opening a container or a mount inventory. A cancel closes the built menu and opens nothing. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMenuEvents {
    @WrapOperation(method = "openMenu(Lnet/minecraft/world/MenuProvider;)Ljava/util/OptionalInt;", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/MenuProvider;createMenu(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/inventory/AbstractContainerMenu;"))
    private AbstractContainerMenu adapt$opening(MenuProvider provider, int id, Inventory inventory, Player player,
                                                Operation<AbstractContainerMenu> original) {
        AbstractContainerMenu menu = original.call(provider, id, inventory, player);
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (menu != null && !ContainerHooks.opening(self, menu)) {
            menu.removed(self);
            return null;
        }
        return menu;
    }

    @Inject(method = "openHorseInventory", at = @At("HEAD"), cancellable = true)
    private void adapt$openingMount(AbstractHorse horse, Container container, CallbackInfo callback) {
        if (!ContainerHooks.openingHorse((ServerPlayer) (Object) this, horse, container)) {
            callback.cancel();
        }
    }
}
