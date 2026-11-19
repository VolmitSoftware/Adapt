package art.arcane.adapt.mod.platform.mixin;

import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

@Mixin(Projectile.class)
public interface ProjectileAccess {
    @Accessor("ownerUUID")
    UUID adapt$owner();
}
