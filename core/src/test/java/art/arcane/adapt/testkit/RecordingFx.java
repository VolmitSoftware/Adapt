package art.arcane.adapt.testkit;

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

import java.util.ArrayList;
import java.util.List;

/** FX host that records every particle and sound instead of sending it. */
public final class RecordingFx implements FxHost {
  private final List<Particle> particles = new ArrayList<>();
  private final List<Sound> sounds = new ArrayList<>();
  private final List<BlockBreak> blockBreaks = new ArrayList<>();

  public record Particle(PlayerRef viewer, ParticleSpec spec, double x, double y, double z, int count) {
  }

  public record Sound(Id sound, double x, double y, double z, float volume, float pitch) {
  }

  public record BlockBreak(WorldRef world, BlockPos block, BlockStateRef state) {
  }

  public synchronized List<Particle> particles() {
    return List.copyOf(particles);
  }

  public synchronized List<Sound> sounds() {
    return List.copyOf(sounds);
  }

  public synchronized List<BlockBreak> blockBreaks() {
    return List.copyOf(blockBreaks);
  }

  public synchronized boolean played(Id sound) {
    for (Sound played : sounds) {
      if (played.sound().equals(sound)) {
        return true;
      }
    }
    return false;
  }

  public synchronized void clear() {
    particles.clear();
    sounds.clear();
    blockBreaks.clear();
  }

  @Override
  public synchronized void particle(WorldRef world, ParticleSpec particle, double x, double y, double z, int count,
                                    double spreadX, double spreadY, double spreadZ, double speed) {
    particles.add(new Particle(null, particle, x, y, z, count));
  }

  @Override
  public synchronized void particle(PlayerRef viewer, ParticleSpec particle, double x, double y, double z, int count,
                                    double spreadX, double spreadY, double spreadZ, double speed, boolean force) {
    particles.add(new Particle(viewer, particle, x, y, z, count));
  }

  @Override
  public synchronized void sound(WorldRef world, double x, double y, double z, Id sound, SoundCategory category,
                                 float volume, float pitch) {
    sounds.add(new Sound(sound, x, y, z, volume, pitch));
  }

  @Override
  public BossBarRef createBossBar(String titleMarkup, BossBarColor color, BossBarStyle style) {
    return TestPlatform.noop(BossBarRef.class);
  }

  @Override
  public void boneMealParticles(WorldRef world, BlockPos block, int count) {
  }

  @Override
  public synchronized void blockBreakEffect(WorldRef world, BlockPos block, BlockStateRef state) {
    blockBreaks.add(new BlockBreak(world, block, state));
  }
}
