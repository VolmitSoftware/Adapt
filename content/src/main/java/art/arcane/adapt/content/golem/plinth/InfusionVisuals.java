package art.arcane.adapt.content.golem.plinth;

import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.platform.fx.ParticleData;
import art.arcane.adapt.platform.value.Loc;

import java.util.Objects;
import java.util.function.Supplier;

final class InfusionVisuals {
  private static final int HUM_INTERVAL_TICKS = 20;
  private static final int SWELL_INTERVAL_TICKS = 6;
  private static final int SHIMMER_INTERVAL_TICKS = 3;
  private static final double CORE_PULL = 0.9D;
  private static final int BURST_SPARKS = 24;
  private static final int BURST_DUST = 35;
  private static final double BURST_SPARK_SPREAD = 0.1D;
  private static final double BURST_SPARK_SPEED = 0.25D;
  private static final double BURST_DUST_SPREAD = 0.35D;
  private static final double FIZZLE_GAP = 0.15D;
  private static final double FIZZLE_TURN = 0.6D;
  private static final double FIZZLE_TRAIL_TURN = 0.1D;
  private static final double FIZZLE_SINK = 0.4D;

  private final Supplier<PlinthHost> host;

  InfusionVisuals(Supplier<PlinthHost> host) {
    this.host = Objects.requireNonNull(host);
  }

  void swirl(Loc center, InfusionRitual ritual) {
    PlinthHost active = host.get();
    if (active == null) {
      return;
    }
    FxEmitter fx = active.fx(center, FxPriority.TRANSITION);
    if (fx.viewerCount() <= 0) {
      return;
    }
    int elapsed = ritual.elapsed();
    int duration = ritual.infusion().durationTicks();
    double progress = InfusionVortex.progress(elapsed, duration);
    InfusionVortex.Phase phase = InfusionVortex.phase(progress);
    InfusionTint tint = ritual.tint();
    int points = InfusionVortex.streamPoints(phase);
    for (int sample = 0; sample < points; sample++) {
      double ticks = elapsed - ((double) sample / points);
      double at = InfusionVortex.progress(ticks, duration);
      ParticleData.Dust dust = sample == 0 ? tint.dust(sample) : tint.trail(sample);
      fx.orbit(Particles.DUST, InfusionVortex.ARMS, InfusionVortex.radius(at), InfusionVortex.angle(ticks, duration), InfusionVortex.height(at), dust);
    }
    if (InfusionVortex.accentDue(phase, elapsed)) {
      fx.orbit(tint.accent(), InfusionVortex.ARMS, InfusionVortex.radius(progress), InfusionVortex.angle(elapsed, duration), InfusionVortex.height(progress), null);
    }
    int core = InfusionVortex.coreParticles(progress);
    if (core > 0) {
      fx.particle(Particles.ENCHANT, core, 0D, InfusionVortex.CORE_HEIGHT, 0D, 0D, CORE_PULL);
    }
    hum(fx, phase, elapsed, progress);
  }

  void collide(Loc center, InfusionTint tint) {
    PlinthHost active = host.get();
    if (active == null) {
      return;
    }
    FxEmitter fx = active.fx(center, FxPriority.TRANSITION)
        .particle(Particles.FLASH, 1, 0D, InfusionVortex.CORE_HEIGHT, 0D, 0D, 0D, new ParticleData.Color(tint.color()))
        .particle(Particles.END_ROD, BURST_SPARKS, 0D, InfusionVortex.CORE_HEIGHT, 0D, BURST_SPARK_SPREAD, BURST_SPARK_SPEED);
    int colors = tint.dust().size();
    for (int color = 0; color < colors; color++) {
      fx.particle(Particles.DUST, BURST_DUST / colors, 0D, InfusionVortex.CORE_HEIGHT, 0D, BURST_DUST_SPREAD, 0D, tint.dust(color));
    }
    fx.chord(Sounds.BLOCK_BEACON_POWER_SELECT, 0.6F, 1.4F, Sounds.BLOCK_AMETHYST_BLOCK_RESONATE, 0.9F, 1.1F, Sounds.ENTITY_PLAYER_LEVELUP, 0.3F, 1.6F);
    active.timeline(center.add(0D, InfusionVortex.PLINTH_HEIGHT, 0D))
        .duration(InfusionVortex.RING_TICKS)
        .priority(FxPriority.TRANSITION)
        .frame((ring, tick, progress) -> ring.ring(Particles.DUST, InfusionVortex.ringRadius(progress), InfusionVortex.RING_POINTS, 0D, tint.dust(tick)))
        .start();
  }

  void fizzle(Loc center, InfusionRitual ritual) {
    PlinthHost active = host.get();
    if (active == null) {
      return;
    }
    int duration = ritual.infusion().durationTicks();
    double progress = InfusionVortex.progress(ritual.elapsed(), duration);
    Scatter scatter = new Scatter(InfusionVortex.radius(progress), InfusionVortex.angle(ritual.elapsed(), duration), InfusionVortex.height(progress),
        ritual.tint());
    active.timeline(center)
        .duration(InfusionVortex.FIZZLE_TICKS)
        .priority(FxPriority.TRANSITION)
        .frame(scatter::render)
        .start();
  }

  private static void hum(FxEmitter fx, InfusionVortex.Phase phase, int elapsed, double progress) {
    switch (phase) {
      case LIFT -> {
        if (elapsed % HUM_INTERVAL_TICKS == 1) {
          fx.sound(Sounds.BLOCK_ENCHANTMENT_TABLE_USE, 0.6F, 0.8F);
        }
      }
      case SWIRL -> {
        if (elapsed % SWELL_INTERVAL_TICKS == 0) {
          fx.sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, InfusionVortex.swellVolume(progress), InfusionVortex.swellPitch(progress));
        }
      }
      case COLLIDE -> {
        if (elapsed % SHIMMER_INTERVAL_TICKS == 0) {
          fx.sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.8F, 2F);
        }
      }
    }
  }

  private record Scatter(double radius, double angle, double height, InfusionTint tint) {
    private void render(FxEmitter fx, int tick, double progress) {
      if (tick == 0) {
        fx.chord(Sounds.BLOCK_BEACON_DEACTIVATE, 0.5F, 0.7F, Sounds.BLOCK_FIRE_EXTINGUISH, 0.35F, 0.6F);
      }
      double reach = InfusionVortex.scatterRadius(radius, progress);
      double turn = angle + (FIZZLE_TURN * progress);
      double sunk = height - (FIZZLE_SINK * progress);
      int points = InfusionVortex.fizzlePoints(tick);
      for (int sample = 0; sample < points; sample++) {
        fx.orbit(Particles.DUST, InfusionVortex.ARMS, reach + (sample * FIZZLE_GAP), turn - (sample * FIZZLE_TRAIL_TURN), sunk, tint.dust(sample));
      }
      if ((tick & 1) == 0) {
        fx.orbit(Particles.SMOKE, InfusionVortex.ARMS, reach, turn, sunk, null);
      }
    }
  }
}
