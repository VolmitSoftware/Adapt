package art.arcane.adapt.mod.platform.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/** Places a registered tree feature by id. */
final class ModTreeFeatures {
    private ModTreeFeatures() {
    }

    static boolean exists(ServerLevel level, ResourceLocation feature) {
        return level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).get(feature) != null;
    }

    static boolean place(ServerLevel level, WorldGenLevel target, ResourceLocation feature, RandomSource random, BlockPos origin) {
        ConfiguredFeature<?, ?> resolved = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).get(feature);
        return resolved != null && resolved.place(target, level.getChunkSource().getGenerator(), random, origin);
    }
}
