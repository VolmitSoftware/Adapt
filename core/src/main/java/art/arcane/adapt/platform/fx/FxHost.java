package art.arcane.adapt.platform.fx;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;

/**
 * Particles, sounds and boss bars. Particle and sound calls are fire-and-forget packet sends and may
 * be made from any thread that owns the region (or the viewer, for viewer-scoped calls).
 */
public interface FxHost {
  /** Particles every nearby player sees. */
  void particle(WorldRef world, ParticleSpec particle, double x, double y, double z,
                int count, double spreadX, double spreadY, double spreadZ, double speed);

  /** Particles only {@code viewer} sees; used by Adapt's per-viewer FX budget. */
  void particle(PlayerRef viewer, ParticleSpec particle, double x, double y, double z,
                int count, double spreadX, double spreadY, double spreadZ, double speed);

  void sound(WorldRef world, double x, double y, double z, Id sound, SoundCategory category, float volume, float pitch);

  BossBarRef createBossBar(String titleMarkup, BossBarColor color, BossBarStyle style);

  /** Vanilla bone meal growth particles and sound at a block (level event 1505). */
  void boneMealParticles(WorldRef world, BlockPos block, int count);

  /** Vanilla block break particles and sound of {@code state} at a block (level event 2001), seen within 64 blocks. */
  void blockBreakEffect(WorldRef world, BlockPos block, BlockStateRef state);
}
