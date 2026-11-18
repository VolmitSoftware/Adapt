package art.arcane.adapt.mod.platform.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Player.class)
public interface PlayerAccess {
    @Accessor("enchantmentSeed")
    void adapt$setEnchantmentSeed(int seed);
}
