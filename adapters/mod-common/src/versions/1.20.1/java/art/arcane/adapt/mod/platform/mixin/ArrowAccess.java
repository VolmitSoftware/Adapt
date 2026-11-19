package art.arcane.adapt.mod.platform.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.alchemy.Potion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Set;

/** Before item data components an arrow keeps its potion and extra effects apart; set both, then refresh the colour. */
@Mixin(Arrow.class)
public interface ArrowAccess {
    @Accessor("potion")
    void adapt$setPotion(Potion potion);

    @Accessor("effects")
    Set<MobEffectInstance> adapt$effects();

    @Invoker("updateColor")
    void adapt$updateColor();
}
