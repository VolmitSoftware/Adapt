package art.arcane.adapt.mod.feature.fx;

import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.ContentAliases;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.fx.ParticleData;
import art.arcane.adapt.platform.fx.ParticleSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SculkChargeParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Converts particle specs to vanilla options, caching the result per spec. A 26.x particle this version lacks plays
 * under its older name or a similar particle, and is skipped (null) when nothing fits.
 */
final class ModParticles {
    private static final int CACHE_LIMIT = 2048;
    private static final ParticleOptions FALLBACK = new DustParticleOptions(color(0xFF0000), 1.0F);
    private static final Map<ParticleSpec, Optional<ParticleOptions>> CACHE = new ConcurrentHashMap<>();

    private ModParticles() {
    }

    static ParticleOptions options(ParticleSpec spec) {
        Optional<ParticleOptions> cached = CACHE.get(spec);
        if (cached != null) {
            return cached.orElse(null);
        }
        Optional<ParticleOptions> created = Optional.ofNullable(create(spec));
        if (CACHE.size() >= CACHE_LIMIT) {
            CACHE.clear();
        }
        CACHE.put(spec, created);
        return created.orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static ParticleOptions create(ParticleSpec spec) {
        ParticleData data = spec.data();
        if (data instanceof ParticleData.Dust dust) {
            return new DustParticleOptions(color(dust.color().packed()), clampScale(dust.size()));
        }
        if (data instanceof ParticleData.DustTransition transition) {
            return new DustColorTransitionOptions(color(transition.from().packed()), color(transition.to().packed()),
                clampScale(transition.size()));
        }
        Id id = ContentAliases.resolveFx(RegistryKind.PARTICLE, spec.type(),
                (Id candidate) -> BuiltInRegistries.PARTICLE_TYPE.containsKey(ModRefs.identifier(candidate)));
        if (id == null) {
            return null;
        }
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(ModRefs.identifier(id));
        if (type instanceof SimpleParticleType simple) {
            return simple;
        }
        String path = id.path();
        if (data instanceof ParticleData.Block block) {
            Object handle = block.state().unwrap();
            BlockState state = handle instanceof BlockState blockState ? blockState : Blocks.STONE.defaultBlockState();
            return new BlockParticleOption((ParticleType<BlockParticleOption>) type, state);
        }
        if (data instanceof ParticleData.Item item) {
            ItemStack stack = ModRefs.item(item.item());
            if (stack == null || stack.isEmpty()) {
                return FALLBACK;
            }
            return new ItemParticleOption((ParticleType<ItemParticleOption>) type, stack.copy());
        }
        float power = data instanceof ParticleData.Power strength ? strength.value() : 1.0F;
        if (path.equals("sculk_charge")) {
            return new SculkChargeParticleOptions(power);
        }
        Rgb rgb = data instanceof ParticleData.Color tint ? tint.color() : new Rgb(255, 255, 255);
        if (path.equals("entity_effect")) {
            return ColorParticleOption.create((ParticleType<ColorParticleOption>) type, 0xFF000000 | rgb.packed());
        }
        if (path.equals("dust")) {
            return new DustParticleOptions(color(rgb.packed()), 1.0F);
        }
        if (path.equals("block") || path.equals("block_marker") || path.equals("falling_dust") || path.equals("dust_pillar")) {
            return new BlockParticleOption((ParticleType<BlockParticleOption>) type, Blocks.STONE.defaultBlockState());
        }
        return ParticleTypes.CRIT;
    }

    private static Vector3f color(int packed) {
        return new Vector3f(((packed >> 16) & 0xFF) / 255.0F, ((packed >> 8) & 0xFF) / 255.0F, (packed & 0xFF) / 255.0F);
    }

    private static float clampScale(float size) {
        return Math.max(0.01F, Math.min(4.0F, size));
    }
}
