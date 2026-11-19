package art.arcane.adapt.gameplay.mod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;

import java.util.OptionalInt;

final class ModFixtureVersion {
    private static final String CUSTOM_MODEL_DATA = "CustomModelData";

    private ModFixtureVersion() {
    }

    static double tps(MinecraftServer server) {
        float millis = server.getAverageTickTime();
        return millis <= 0.0F ? 20.0D : Math.min(20.0D, 1000.0D / millis);
    }

    static void tame(TamableAnimal tamable, ServerPlayer owner) {
        tamable.setTame(true);
        tamable.setOwnerUUID(owner.getUUID());
    }

    static OptionalInt customModelData(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains(CUSTOM_MODEL_DATA, Tag.TAG_ANY_NUMERIC) ? OptionalInt.empty() : OptionalInt.of(tag.getInt(CUSTOM_MODEL_DATA));
    }
}
