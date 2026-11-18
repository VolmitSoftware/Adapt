package art.arcane.adapt.mod.feature.fx;

import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.fx.ParticleData;
import art.arcane.adapt.platform.fx.ParticleSpec;
import art.arcane.adapt.platform.value.Rgb;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.core.particles.SculkChargeParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.particles.SpellParticleOption;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Converts particle specs to vanilla options, caching the result per spec. */
final class ModParticles {
    private static final int CACHE_LIMIT = 2048;
    private static final ParticleOptions FALLBACK = new DustParticleOptions(0xFF0000, 1.0F);
    private static final Map<ParticleSpec, ParticleOptions> CACHE = new ConcurrentHashMap<>();

    private ModParticles() {
    }

    static ParticleOptions options(ParticleSpec spec) {
        ParticleOptions cached = CACHE.get(spec);
        if (cached != null) {
            return cached;
        }
        ParticleOptions created = create(spec);
        if (CACHE.size() >= CACHE_LIMIT) {
            CACHE.clear();
        }
        CACHE.put(spec, created);
        return created;
    }

    @SuppressWarnings("unchecked")
    private static ParticleOptions create(ParticleSpec spec) {
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.getValue(ModRefs.identifier(spec.type()));
        ParticleData data = spec.data();
        if (data instanceof ParticleData.Dust dust) {
            return new DustParticleOptions(dust.color().packed(), clampScale(dust.size()));
        }
        if (data instanceof ParticleData.DustTransition transition) {
            return new DustColorTransitionOptions(transition.from().packed(), transition.to().packed(), clampScale(transition.size()));
        }
        if (type == null) {
            return FALLBACK;
        }
        if (type instanceof SimpleParticleType simple) {
            return simple;
        }
        String path = spec.type().path();
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
            return new ItemParticleOption((ParticleType<ItemParticleOption>) type, ItemStackTemplate.fromNonEmptyStack(stack));
        }
        float power = data instanceof ParticleData.Power strength ? strength.value() : 1.0F;
        if (path.equals("dragon_breath")) {
            return PowerParticleOption.create((ParticleType<PowerParticleOption>) type, power);
        }
        if (path.equals("sculk_charge")) {
            return new SculkChargeParticleOptions(power);
        }
        Rgb color = data instanceof ParticleData.Color tint ? tint.color() : new Rgb(255, 255, 255);
        if (path.equals("effect") || path.equals("instant_effect")) {
            return SpellParticleOption.create((ParticleType<SpellParticleOption>) type, color.packed(), 1.0F);
        }
        if (path.equals("entity_effect") || path.equals("tinted_leaves") || path.equals("flash")) {
            return ColorParticleOption.create((ParticleType<ColorParticleOption>) type, 0xFF000000 | color.packed());
        }
        if (path.equals("dust")) {
            return new DustParticleOptions(color.packed(), 1.0F);
        }
        if (path.equals("block") || path.equals("block_marker") || path.equals("falling_dust") || path.equals("dust_pillar") || path.equals("block_crumble")) {
            return new BlockParticleOption((ParticleType<BlockParticleOption>) type, Blocks.STONE.defaultBlockState());
        }
        return ParticleTypes.CRIT;
    }

    private static float clampScale(float size) {
        return Math.max(0.01F, Math.min(4.0F, size));
    }
}
