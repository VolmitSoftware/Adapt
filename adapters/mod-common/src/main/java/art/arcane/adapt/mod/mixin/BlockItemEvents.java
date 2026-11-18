package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.InteractHooks;
import art.arcane.adapt.mod.event.PlaceDeferral;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/** Player block placement, with the new block already in the world as Paper posts it and its onPlace held until then. */
@Mixin(BlockItem.class)
public abstract class BlockItemEvents {
    @WrapOperation(method = "place", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/BlockItem;placeBlock(Lnet/minecraft/world/item/context/BlockPlaceContext;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean adapt$placing(BlockItem item, BlockPlaceContext context, BlockState placement, Operation<Boolean> original) {
        BlockState replaced = context.getLevel().getBlockState(context.getClickedPos());
        ItemStack before = context.getItemInHand().copy();
        boolean deferring = InteractHooks.postsPlace(context);
        List<PlaceDeferral.Pending> previous = deferring ? PlaceDeferral.begin() : null;
        boolean stands = false;
        try {
            if (!original.call(item, context, placement)) {
                return false;
            }
            stands = !InteractHooks.placed(context, replaced, before);
            return stands;
        } finally {
            if (deferring) {
                PlaceDeferral.finish(previous, stands);
            }
        }
    }
}
