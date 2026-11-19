package art.arcane.adapt.gameplay.mod;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

import java.util.OptionalInt;

final class ModFixtureVersion {
    private ModFixtureVersion() {
    }

    static double tps(MinecraftServer server) {
        long nanos = server.getAverageTickTimeNanos();
        return nanos <= 0L ? 20.0D : Math.min(20.0D, 1.0E9D / nanos);
    }

    static void tame(TamableAnimal tamable, ServerPlayer owner) {
        tamable.setTame(true, true);
        tamable.setOwnerUUID(owner.getUUID());
    }

    static OptionalInt customModelData(ItemStack stack) {
        CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return data == null ? OptionalInt.empty() : OptionalInt.of(data.value());
    }
}
