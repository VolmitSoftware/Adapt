package art.arcane.adapt.mod.feature.fx;

import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.mod.platform.ModConversions;
import art.arcane.adapt.platform.ContentAliases;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.fx.BossBarColor;
import art.arcane.adapt.platform.fx.BossBarRef;
import art.arcane.adapt.platform.fx.BossBarStyle;
import art.arcane.adapt.platform.fx.FxHost;
import art.arcane.adapt.platform.fx.ParticleSpec;
import art.arcane.adapt.platform.fx.SoundCategory;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ModFxHost implements FxHost {
    private final Map<Id, Holder<SoundEvent>> sounds = new ConcurrentHashMap<>();

    @Override
    public void particle(WorldRef world, ParticleSpec particle, double x, double y, double z,
                         int count, double spreadX, double spreadY, double spreadZ, double speed) {
        ServerLevel level = ModRefs.world(world);
        if (level == null) {
            return;
        }
        ParticleOptions options = ModParticles.options(particle);
        if (options != null) {
            level.sendParticles(options, x, y, z, count, spreadX, spreadY, spreadZ, speed);
        }
    }

    @Override
    public void particle(PlayerRef viewer, ParticleSpec particle, double x, double y, double z,
                         int count, double spreadX, double spreadY, double spreadZ, double speed, boolean force) {
        ServerPlayer player = ModRefs.player(viewer);
        if (player == null || player.hasDisconnected()) {
            return;
        }
        ParticleOptions options = ModParticles.options(particle);
        if (options == null) {
            return;
        }
        player.connection.send(new ClientboundLevelParticlesPacket(options, force, x, y, z,
                (float) spreadX, (float) spreadY, (float) spreadZ, (float) speed, count));
    }

    @Override
    public void sound(WorldRef world, double x, double y, double z, Id sound, SoundCategory category, float volume, float pitch) {
        ServerLevel level = ModRefs.world(world);
        if (level == null) {
            return;
        }
        level.playSound(null, x, y, z, sound(sound).value(), source(category), volume, pitch);
    }

    @Override
    public BossBarRef createBossBar(String titleMarkup, BossBarColor color, BossBarStyle style) {
        return new ModBossBar(titleMarkup, color, style);
    }

    @Override
    public void boneMealParticles(WorldRef world, BlockPos block, int count) {
        ServerLevel level = ModRefs.world(world);
        if (level != null) {
            level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, ModConversions.pos(block), count);
        }
    }

    @Override
    public void blockBreakEffect(WorldRef world, BlockPos block, BlockStateRef state) {
        ServerLevel level = ModRefs.world(world);
        if (level != null) {
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, ModConversions.pos(block), Block.getId(ModRefs.state(state)));
        }
    }

    /**
     * Vanilla sound holder for a 26.x id, under its older name or a similar sound when this version lacks it; unknown
     * ids play as a resource-pack sound of that name.
     */
    public Holder<SoundEvent> sound(Id id) {
        Holder<SoundEvent> cached = sounds.get(id);
        if (cached != null) {
            return cached;
        }
        Id resolved = ContentAliases.resolveFx(RegistryKind.SOUND, id,
                (Id candidate) -> BuiltInRegistries.SOUND_EVENT.containsKey(ModRefs.identifier(candidate)));
        ResourceLocation identifier = ModRefs.identifier(resolved == null ? id : resolved);
        Optional<Holder.Reference<SoundEvent>> registered =
                BuiltInRegistries.SOUND_EVENT.getHolder(ResourceKey.create(Registries.SOUND_EVENT, identifier));
        Holder<SoundEvent> holder = registered.isPresent()
                ? registered.get()
                : Holder.direct(SoundEvent.createVariableRangeEvent(identifier));
        sounds.put(id, holder);
        return holder;
    }

    public static SoundSource source(SoundCategory category) {
        return switch (category) {
            case MASTER -> SoundSource.MASTER;
            case MUSIC -> SoundSource.MUSIC;
            case RECORDS -> SoundSource.RECORDS;
            case WEATHER -> SoundSource.WEATHER;
            case BLOCKS -> SoundSource.BLOCKS;
            case HOSTILE -> SoundSource.HOSTILE;
            case NEUTRAL -> SoundSource.NEUTRAL;
            case PLAYERS -> SoundSource.PLAYERS;
            case AMBIENT -> SoundSource.AMBIENT;
            case VOICE -> SoundSource.VOICE;
        };
    }
}
