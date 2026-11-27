package art.arcane.adapt.api.fx;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.fx.ParticleData;
import art.arcane.adapt.platform.fx.ParticleSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import org.jetbrains.annotations.ApiStatus;

/**
 * Particles and sounds anchored at one point, sent to the players near it. Adapt hands one out per effect; every
 * shape method draws around the anchor and returns this emitter, so calls chain. Particle counts are scaled down by
 * the effect budget of the emitter's {@link FxPriority}, and an emitter whose component has particles or sounds
 * turned off skips that half silently. Colour particles without data ({@link Particles#DUST}) take the owning skill's
 * colour.
 */
@ApiStatus.Experimental
public interface FxEmitter {
  /**
   * {@code count} particles at the anchor moved by {@code (ox, oy, oz)}, scattered up to {@code spread} blocks on
   * each axis, with the particle's own {@code speed}.
   */
  FxEmitter particle(ParticleSpec particle, int count, double ox, double oy, double oz, double spread, double speed);

  /**
   * As {@link #particle(ParticleSpec, int, double, double, double, double, double)}, with {@code data} in place of
   * the spec's.
   */
  FxEmitter particle(ParticleSpec particle, int count, double ox, double oy, double oz, double spread, double speed,
                     ParticleData data);

  /**
   * Up to {@code points} particles, at most 32, spaced evenly on a level circle round the anchor, raised by
   * {@code yOffset}.
   */
  FxEmitter ring(ParticleSpec particle, double radius, int points, double yOffset);

  /** As {@link #ring(ParticleSpec, double, int, double)}, with {@code data} in place of the spec's. */
  FxEmitter ring(ParticleSpec particle, double radius, int points, double yOffset, ParticleData data);

  /**
   * {@code points} particles on part of a level circle round the anchor, starting at {@code yawRadians} and running
   * {@code spanRadians} round, raised by {@code yOffset}.
   */
  FxEmitter arc(ParticleSpec particle, double radius, int points, double yawRadians, double spanRadians,
                double yOffset);

  /**
   * {@code points} particles on a spiral of two turns that rises from the anchor to {@code height}, turned by
   * {@code phaseRadians}; advance the phase each tick to spin it.
   */
  FxEmitter helix(ParticleSpec particle, double radius, double height, int points, double phaseRadians);

  /** As {@link #helix(ParticleSpec, double, double, int, double)}, with {@code data} in place of the spec's. */
  FxEmitter helix(ParticleSpec particle, double radius, double height, int points, double phaseRadians,
                  ParticleData data);

  /**
   * One particle per arm, {@code arms} spaced evenly on a level circle round the anchor, starting at
   * {@code angleRadians} and raised by {@code yOffset}; {@code data} may be null for the spec's own.
   */
  FxEmitter orbit(ParticleSpec particle, int arms, double radius, double angleRadians, double yOffset,
                  ParticleData data);

  /** A line from the anchor to the absolute point {@code (tx, ty, tz)}. */
  FxEmitter line(ParticleSpec particle, double tx, double ty, double tz, int points);

  /** {@code count} particles scattered up to {@code spread} blocks round the anchor on each axis. */
  FxEmitter burst(ParticleSpec particle, int count, double spread);

  /** {@code count} particles stacked straight up from the anchor to {@code height}. */
  FxEmitter column(ParticleSpec particle, int count, double height);

  /** Up to {@code points} particles, at most 48, spread over the upper half of a sphere round the anchor. */
  FxEmitter dome(ParticleSpec particle, double radius, int points);

  /**
   * {@code points} particles along the direction {@code (dirX, dirY, dirZ)} from the anchor, out to {@code length}
   * blocks; the first sits one step past the anchor. A zero direction draws nothing.
   */
  FxEmitter trail(ParticleSpec particle, double dirX, double dirY, double dirZ, double length, int points);

  /** A level {@link #ring} of dust at the anchor's height in the emitter's {@link #color()}. */
  FxEmitter dustRing(double radius, int points, float size);

  /** A dust ring in {@code customColor}; null means the emitter's colour. */
  FxEmitter dustRing(Rgb customColor, double radius, int points, float size);

  /** A {@link #burst} of dust in the emitter's {@link #color()}. */
  FxEmitter dustBurst(int count, double spread, float size);

  /** A dust burst in {@code customColor}; null means the emitter's colour. */
  FxEmitter dustBurst(Rgb customColor, int count, double spread, float size);

  /** A {@link #helix} of dust in the emitter's {@link #color()}. */
  FxEmitter dustHelix(double radius, double height, int points, double phaseRadians, float size);

  /** A dust helix in {@code customColor}; null means the emitter's colour. */
  FxEmitter dustHelix(Rgb customColor, double radius, double height, int points, double phaseRadians, float size);

  /**
   * Plays a sound at the anchor for players within 16 blocks, or {@code 16 * volume} when that is farther. Under load
   * the sounds of every priority but {@link FxPriority#GAMEPLAY} may be skipped.
   */
  FxEmitter sound(Id sound, float volume, float pitch);

  /** Two sounds at once, each with its own volume and pitch; both play or both are skipped. */
  FxEmitter chord(Id a, float va, float pa, Id b, float vb, float pb);

  /** Three sounds at once, each with its own volume and pitch; all play or all are skipped. */
  FxEmitter chord(Id a, float va, float pa, Id b, float vb, float pb, Id c, float vc, float pc);

  /** The colour dust particles without their own colour are drawn in. */
  Rgb color();

  /** How many players this emitter sends to; 0 when nobody is near or effects are off. */
  int viewerCount();

  /** Sends this emitter's particles forced: shown whatever a viewer's particle setting and from far away. */
  FxEmitter forced();

  /** An emitter that sends only to {@code viewer}, or one that sends nothing when the viewer is not near. */
  FxEmitter only(PlayerRef viewer);

  /** An emitter that sends to everyone this one does except {@code viewer}. */
  FxEmitter except(PlayerRef viewer);
}
