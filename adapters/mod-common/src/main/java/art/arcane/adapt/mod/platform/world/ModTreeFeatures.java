package art.arcane.adapt.mod.platform.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;

/** Places a registered tree feature by id. */
final class ModTreeFeatures {
    private ModTreeFeatures() {
    }

    static boolean exists(ServerLevel level, Identifier feature) {
        return level.registryAccess().lookupOrThrow(Registries.FEATURE).getValue(feature) != null;
    }

    static boolean place(ServerLevel level, WorldGenLevel target, Identifier feature, RandomSource random, BlockPos origin) {
        Feature resolved = level.registryAccess().lookupOrThrow(Registries.FEATURE).getValue(feature);
        return resolved != null && resolved.place(target, level.getChunkSource().getGenerator(), random, origin);
    }
}
