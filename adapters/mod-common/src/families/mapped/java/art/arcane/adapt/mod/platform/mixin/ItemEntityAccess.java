package art.arcane.adapt.mod.platform.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

@Mixin(ItemEntity.class)
public interface ItemEntityAccess {
    @Accessor("pickupDelay")
    int adapt$pickupDelay();

    @Accessor("thrower")
    UUID adapt$thrower();

    @Accessor("thrower")
    void adapt$setThrower(UUID thrower);

    @Accessor("cachedThrower")
    void adapt$setCachedThrower(Entity thrower);

    @Accessor("age")
    void adapt$setAge(int age);

    @Accessor("target")
    UUID adapt$target();
}
