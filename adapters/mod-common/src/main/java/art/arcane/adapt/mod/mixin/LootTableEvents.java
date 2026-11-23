package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.LootHooks;
import net.minecraft.world.Container;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Container loot: vanilla fills a container from its loot table once, when it is first opened. */
@Mixin(LootTable.class)
public abstract class LootTableEvents {
    @Inject(method = "fill", at = @At("HEAD"))
    private void adapt$filling(Container container, LootParams params, long seed, CallbackInfo callback) {
        LootHooks.filling(params);
    }
}
