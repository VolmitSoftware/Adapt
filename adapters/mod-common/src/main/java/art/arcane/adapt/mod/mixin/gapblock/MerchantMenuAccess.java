package art.arcane.adapt.mod.mixin.gapblock;

import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MerchantMenu.class)
public interface MerchantMenuAccess {
    @Accessor("trader")
    Merchant adapt$trader();
}
