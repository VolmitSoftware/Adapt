package art.arcane.adapt.api.fx;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.fx.FxHost;
import art.arcane.adapt.platform.fx.ParticleData;
import art.arcane.adapt.platform.fx.ParticleSpec;
import art.arcane.adapt.platform.fx.SoundCategory;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public final class FxEmitter {
  static final FxEmitter NO_OP = new FxEmitter();
  private static final double[] CIRCLE_8 = buildCircle(8);
  private static final double[] CIRCLE_12 = buildCircle(12);
  private static final double[] CIRCLE_16 = buildCircle(16);
  private static final double[] CIRCLE_24 = buildCircle(24);
  private static final double[] CIRCLE_32 = buildCircle(32);
  private static final double[] DOME_48 = buildDome(48);
  private static final int HELIX_REVOLUTIONS = 2;
  private static final double TWO_PI = Math.PI * 2.0D;
  private static final float DEFAULT_DUST_SIZE = 1.0F;
  private static final Id DUST = Id.minecraft("dust");
  private static final Id DUST_COLOR_TRANSITION = Id.minecraft("dust_color_transition");
  private static final Id ENTITY_EFFECT = Id.minecraft("entity_effect");
  private static final Id FLASH = Id.minecraft("flash");
  private static final Id TINTED_LEAVES = Id.minecraft("tinted_leaves");

  private final FxHost host;
  private final WorldRef world;
  private final double x;
  private final double y;
  private final double z;
  private final FxPriority priority;
  private final boolean particlesOn;
  private final boolean soundsOn;
  private final Rgb color;
  private final FxViewers.Snapshot viewersSnapshot;
  private final int[] viewerIndices;
  private final int viewerCount;
  private final Consumer<Throwable> failureHandler;
  private int emittedParticles;
  private boolean restrictedAudience;
  private boolean forced;

  private FxEmitter() {
    this.host = null;
    this.world = null;
    this.x = 0;
    this.y = 0;
    this.z = 0;
    this.priority = FxPriority.AMBIENT;
    this.particlesOn = false;
    this.soundsOn = false;
    this.color = Fx.WHITE;
    this.viewersSnapshot = null;
    this.viewerIndices = new int[0];
    this.viewerCount = 0;
    this.failureHandler = null;
  }

  private FxEmitter(FxHost host, WorldRef world, double x, double y, double z, FxPriority priority, boolean particlesOn, boolean soundsOn, Rgb color, FxViewers.Snapshot viewersSnapshot, int[] viewerIndices, int viewerCount, Consumer<Throwable> failureHandler) {
    this.host = host;
    this.world = world;
    this.x = x;
    this.y = y;
    this.z = z;
    this.priority = priority;
    this.particlesOn = particlesOn;
    this.soundsOn = soundsOn;
    this.color = color;
    this.viewersSnapshot = viewersSnapshot;
    this.viewerIndices = viewerIndices;
    this.viewerCount = viewerCount;
    this.failureHandler = failureHandler;
  }

  static FxEmitter create(WorldRef world, double x, double y, double z, FxPriority priority, double cullRadius, boolean particles, boolean sounds, Rgb color) {
    return create(world, x, y, z, priority, cullRadius, particles, sounds, color, null);
  }

  static FxEmitter create(WorldRef world, double x, double y, double z, FxPriority priority, double cullRadius, boolean particles, boolean sounds, Rgb color, Consumer<Throwable> failureHandler) {
    if (world == null || priority == null || (!particles && !sounds)) {
      return NO_OP;
    }
    FxHost host = Fx.host();
    if (host == null) {
      return NO_OP;
    }

    double radius = Math.min(FxViewers.MAX_CULL_RADIUS, Math.max(1.0D, cullRadius));
    FxViewers.Snapshot current = FxViewers.current();
    int count = current.countViewers(world, x, y, z, radius);
    if (count <= 0) {
      return NO_OP;
    }

    int[] indices = new int[count];
    int filled = current.fillViewerIndices(world, x, y, z, radius, indices);
    if (filled <= 0) {
      return NO_OP;
    }

    return new FxEmitter(host, world, x, y, z, priority, particles, sounds, color == null ? Fx.WHITE : color, current, indices, filled, failureHandler);
  }

  public FxEmitter particle(ParticleSpec particle, int count, double ox, double oy, double oz, double spread, double speed) {
    return particle(particle, count, ox, oy, oz, spread, speed, null);
  }

  public FxEmitter particle(ParticleSpec particle, int count, double ox, double oy, double oz, double spread, double speed, ParticleData data) {
    if (particle == null) {
      return this;
    }

    int allowed = budget(count);
    if (allowed <= 0) {
      return this;
    }

    double px = x + ox;
    double py = y + oy;
    double pz = z + oz;
    ParticleSpec spec = resolve(particle, data, color);
    dispatch(emission(player -> host.particle(player, spec, px, py, pz, allowed, spread, spread, spread, speed, forced)));
    return this;
  }

  public FxEmitter ring(ParticleSpec particle, double radius, int points, double yOffset) {
    return ring(particle, radius, points, yOffset, null);
  }

  public FxEmitter ring(ParticleSpec particle, double radius, int points, double yOffset, ParticleData data) {
    if (particle == null) {
      return this;
    }

    double[] lut = circleLut(points);
    int total = lut.length >> 1;
    int allowed = budget(Math.min(Math.max(1, points), total));
    if (allowed <= 0) {
      return this;
    }

    double py = y + yOffset;
    ParticleSpec spec = resolve(particle, data, color);
    dispatch(emission(player -> {
      for (int k = 0; k < allowed; k++) {
        int i = (int) (((long) k * total) / allowed);
        host.particle(player, spec, x + (lut[i << 1] * radius), py, z + (lut[(i << 1) + 1] * radius), 1, 0, 0, 0, 0, forced);
      }
    }));
    return this;
  }

  public FxEmitter arc(ParticleSpec particle, double radius, int points, double yawRadians, double spanRadians, double yOffset) {
    if (particle == null || spanRadians == 0) {
      return this;
    }

    int allowed = budget(Math.max(1, points));
    if (allowed <= 0) {
      return this;
    }

    int total = CIRCLE_32.length >> 1;
    double cosYaw = Math.cos(yawRadians);
    double sinYaw = Math.sin(yawRadians);
    double py = y + yOffset;
    ParticleSpec spec = resolve(particle, null, color);
    dispatch(emission(player -> {
      int last = -1;
      for (int k = 0; k < allowed; k++) {
        double fraction = allowed == 1 ? 0.5D : (double) k / (allowed - 1);
        int i = Math.floorMod((int) Math.round((fraction * spanRadians / TWO_PI) * total), total);
        if (i == last) {
          continue;
        }
        last = i;
        double lx = CIRCLE_32[i << 1] * radius;
        double lz = CIRCLE_32[(i << 1) + 1] * radius;
        host.particle(player, spec, x + ((cosYaw * lx) - (sinYaw * lz)), py, z + ((sinYaw * lx) + (cosYaw * lz)), 1, 0, 0, 0, 0, forced);
      }
    }));
    return this;
  }

  public FxEmitter helix(ParticleSpec particle, double radius, double height, int points, double phaseRadians) {
    return helix(particle, radius, height, points, phaseRadians, null);
  }

  public FxEmitter helix(ParticleSpec particle, double radius, double height, int points, double phaseRadians, ParticleData data) {
    if (particle == null) {
      return this;
    }

    int allowed = budget(Math.max(1, points));
    if (allowed <= 0) {
      return this;
    }

    int total = CIRCLE_32.length >> 1;
    double cosPhase = Math.cos(phaseRadians);
    double sinPhase = Math.sin(phaseRadians);
    ParticleSpec spec = resolve(particle, data, color);
    dispatch(emission(player -> {
      for (int k = 0; k < allowed; k++) {
        double t = allowed == 1 ? 0.5D : (double) k / (allowed - 1);
        int i = (int) (((long) k * total * HELIX_REVOLUTIONS) / allowed) % total;
        double lx = CIRCLE_32[i << 1] * radius;
        double lz = CIRCLE_32[(i << 1) + 1] * radius;
        host.particle(player, spec, x + ((cosPhase * lx) - (sinPhase * lz)), y + (t * height), z + ((sinPhase * lx) + (cosPhase * lz)), 1, 0, 0, 0, 0, forced);
      }
    }));
    return this;
  }

  public FxEmitter orbit(ParticleSpec particle, int arms, double radius, double angleRadians, double yOffset, ParticleData data) {
    if (particle == null || arms <= 0) {
      return this;
    }

    int allowed = budget(arms);
    if (allowed <= 0) {
      return this;
    }

    double startCos = Math.cos(angleRadians);
    double startSin = Math.sin(angleRadians);
    double stepCos = Math.cos(TWO_PI / arms);
    double stepSin = Math.sin(TWO_PI / arms);
    double py = y + yOffset;
    ParticleSpec spec = resolve(particle, data, color);
    dispatch(emission(player -> {
      double armCos = startCos;
      double armSin = startSin;
      for (int k = 0; k < allowed; k++) {
        host.particle(player, spec, x + (armCos * radius), py, z + (armSin * radius), 1, 0, 0, 0, 0, forced);
        double nextCos = (armCos * stepCos) - (armSin * stepSin);
        armSin = (armSin * stepCos) + (armCos * stepSin);
        armCos = nextCos;
      }
    }));
    return this;
  }

  public FxEmitter line(ParticleSpec particle, double tx, double ty, double tz, int points) {
    if (particle == null) {
      return this;
    }

    int allowed = budget(Math.max(1, points));
    if (allowed <= 0) {
      return this;
    }

    double dx = tx - x;
    double dy = ty - y;
    double dz = tz - z;
    ParticleSpec spec = resolve(particle, null, color);
    dispatch(emission(player -> {
      for (int k = 0; k < allowed; k++) {
        double t = allowed == 1 ? 0.5D : (double) k / (allowed - 1);
        host.particle(player, spec, x + (dx * t), y + (dy * t), z + (dz * t), 1, 0, 0, 0, 0, forced);
      }
    }));
    return this;
  }

  public FxEmitter burst(ParticleSpec particle, int count, double spread) {
    if (particle == null) {
      return this;
    }

    int allowed = budget(count);
    if (allowed <= 0) {
      return this;
    }

    ParticleSpec spec = resolve(particle, null, color);
    dispatch(emission(player -> host.particle(player, spec, x, y, z, allowed, spread, spread, spread, 0, forced)));
    return this;
  }

  public FxEmitter column(ParticleSpec particle, int count, double height) {
    if (particle == null) {
      return this;
    }

    int allowed = budget(count);
    if (allowed <= 0) {
      return this;
    }

    ParticleSpec spec = resolve(particle, null, color);
    dispatch(emission(player -> {
      for (int k = 0; k < allowed; k++) {
        double t = allowed == 1 ? 0.5D : (double) k / (allowed - 1);
        host.particle(player, spec, x, y + (t * height), z, 1, 0, 0, 0, 0, forced);
      }
    }));
    return this;
  }

  public FxEmitter dome(ParticleSpec particle, double radius, int points) {
    if (particle == null) {
      return this;
    }

    int total = DOME_48.length / 3;
    int allowed = budget(Math.min(Math.max(1, points), total));
    if (allowed <= 0) {
      return this;
    }

    ParticleSpec spec = resolve(particle, null, color);
    dispatch(emission(player -> {
      for (int k = 0; k < allowed; k++) {
        int base = ((int) (((long) k * total) / allowed)) * 3;
        host.particle(player, spec, x + (DOME_48[base] * radius), y + (DOME_48[base + 1] * radius), z + (DOME_48[base + 2] * radius), 1, 0, 0, 0, 0, forced);
      }
    }));
    return this;
  }

  public FxEmitter trail(ParticleSpec particle, double dirX, double dirY, double dirZ, double length, int points) {
    if (particle == null) {
      return this;
    }

    double lengthSq = (dirX * dirX) + (dirY * dirY) + (dirZ * dirZ);
    if (lengthSq < 1.0E-9D) {
      return this;
    }

    int allowed = budget(Math.max(1, points));
    if (allowed <= 0) {
      return this;
    }

    double scale = length / Math.sqrt(lengthSq);
    double sx = dirX * scale;
    double sy = dirY * scale;
    double sz = dirZ * scale;
    ParticleSpec spec = resolve(particle, null, color);
    dispatch(emission(player -> {
      for (int k = 0; k < allowed; k++) {
        double t = (double) (k + 1) / allowed;
        host.particle(player, spec, x + (sx * t), y + (sy * t), z + (sz * t), 1, 0, 0, 0, 0, forced);
      }
    }));
    return this;
  }

  public FxEmitter dustRing(double radius, int points, float size) {
    return dustRing(color, radius, points, size);
  }

  public FxEmitter dustRing(Rgb customColor, double radius, int points, float size) {
    if (!particlesOn) {
      return this;
    }
    return ring(Particles.REDSTONE, radius, points, 0, new ParticleData.Dust(customColor == null ? color : customColor, size));
  }

  public FxEmitter dustBurst(int count, double spread, float size) {
    return dustBurst(color, count, spread, size);
  }

  public FxEmitter dustBurst(Rgb customColor, int count, double spread, float size) {
    if (!particlesOn) {
      return this;
    }
    return particle(Particles.REDSTONE, count, 0, 0, 0, spread, 0, new ParticleData.Dust(customColor == null ? color : customColor, size));
  }

  public FxEmitter dustHelix(double radius, double height, int points, double phaseRadians, float size) {
    return dustHelix(color, radius, height, points, phaseRadians, size);
  }

  public FxEmitter dustHelix(Rgb customColor, double radius, double height, int points, double phaseRadians, float size) {
    if (!particlesOn) {
      return this;
    }
    return helix(Particles.REDSTONE, radius, height, points, phaseRadians, new ParticleData.Dust(customColor == null ? color : customColor, size));
  }

  public FxEmitter sound(Id sound, float volume, float pitch) {
    if (soundShed()) {
      return this;
    }
    emitSound(sound, volume, pitch);
    return this;
  }

  public FxEmitter chord(Id a, float va, float pa, Id b, float vb, float pb) {
    if (soundShed()) {
      return this;
    }
    emitSound(a, va, pa);
    emitSound(b, vb, pb);
    return this;
  }

  public FxEmitter chord(Id a, float va, float pa, Id b, float vb, float pb, Id c, float vc, float pc) {
    if (soundShed()) {
      return this;
    }
    emitSound(a, va, pa);
    emitSound(b, vb, pb);
    emitSound(c, vc, pc);
    return this;
  }

  private boolean soundShed() {
    if (!soundsOn || world == null) {
      return true;
    }
    if (priority == FxPriority.GAMEPLAY) {
      return false;
    }

    double scalar = FxBudget.densityScalar(priority);
    return scalar <= 0.0D || (scalar < 1.0D && ThreadLocalRandom.current().nextDouble() >= scalar);
  }

  private void emitSound(Id sound, float volume, float pitch) {
    if (sound == null) {
      return;
    }

    double radius = soundRadius(volume);
    Loc location = new Loc(world, x, y, z);
    if (restrictedAudience) {
      FxDispatch.Emission soundEmission = emission(player -> player.playSound(location, sound, SoundCategory.MASTER, volume, pitch));
      for (int viewerIndex : viewerIndices) {
        if (viewersSnapshot.withinRange(viewerIndex, x, y, z, radius)) {
          viewersSnapshot.dispatch(viewerIndex, soundEmission);
        }
      }
      return;
    }
    viewersSnapshot.dispatch(world, x, y, z, radius, emission(player -> player.playSound(location, sound, SoundCategory.MASTER, volume, pitch)));
  }

  static double soundRadius(float volume) {
    return Math.min(FxViewers.MAX_CULL_RADIUS, Math.max(16.0D, 16.0D * volume));
  }

  public Rgb color() {
    return color;
  }

  public int viewerCount() {
    return viewerCount;
  }

  /** Sends this emitter's particles forced: shown whatever a viewer's particle setting and from far away. */
  public FxEmitter forced() {
    if (this != NO_OP) {
      forced = true;
    }
    return this;
  }

  public FxEmitter only(PlayerRef viewer) {
    if (viewer == null || viewersSnapshot == null || viewerCount == 0) {
      return NO_OP;
    }
    int index = viewersSnapshot.indexOf(viewer);
    for (int position = 0; position < viewerCount; position++) {
      if (viewerIndices[position] == index) {
        FxEmitter filtered = new FxEmitter(host, world, x, y, z, priority, particlesOn, soundsOn, color,
            viewersSnapshot, new int[]{index}, 1, failureHandler);
        filtered.emittedParticles = emittedParticles;
        filtered.restrictedAudience = true;
        filtered.forced = forced;
        return filtered;
      }
    }
    return NO_OP;
  }

  public FxEmitter except(PlayerRef viewer) {
    if (viewer == null || viewersSnapshot == null || viewerCount == 0) {
      return this;
    }
    int excluded = viewersSnapshot.indexOf(viewer);
    int removed = -1;
    for (int position = 0; position < viewerCount; position++) {
      if (viewerIndices[position] == excluded) {
        removed = position;
        break;
      }
    }
    if (removed < 0) {
      FxEmitter filtered = new FxEmitter(host, world, x, y, z, priority, particlesOn, soundsOn, color,
          viewersSnapshot, viewerIndices, viewerCount, failureHandler);
      filtered.emittedParticles = emittedParticles;
      filtered.restrictedAudience = true;
      filtered.forced = forced;
      return filtered;
    }
    if (viewerCount == 1) {
      return NO_OP;
    }
    int[] indices = new int[viewerCount - 1];
    System.arraycopy(viewerIndices, 0, indices, 0, removed);
    System.arraycopy(viewerIndices, removed + 1, indices, removed, viewerCount - removed - 1);
    FxEmitter filtered = new FxEmitter(host, world, x, y, z, priority, particlesOn, soundsOn, color,
        viewersSnapshot, indices, indices.length, failureHandler);
    filtered.emittedParticles = emittedParticles;
    filtered.restrictedAudience = true;
    filtered.forced = forced;
    return filtered;
  }

  private static ParticleSpec resolve(ParticleSpec particle, ParticleData data, Rgb fallbackColor) {
    if (data != null) {
      return new ParticleSpec(particle.type(), data);
    }
    if (!(particle.data() instanceof ParticleData.None)) {
      return particle;
    }
    Id type = particle.type();
    Rgb color = fallbackColor == null ? Fx.WHITE : fallbackColor;
    if (type.equals(DUST)) {
      return new ParticleSpec(type, new ParticleData.Dust(color, DEFAULT_DUST_SIZE));
    }
    if (type.equals(DUST_COLOR_TRANSITION)) {
      return new ParticleSpec(type, new ParticleData.DustTransition(color, color, DEFAULT_DUST_SIZE));
    }
    if (type.equals(ENTITY_EFFECT) || type.equals(FLASH) || type.equals(TINTED_LEAVES)) {
      return new ParticleSpec(type, new ParticleData.Color(color));
    }
    return particle;
  }

  private FxDispatch.Emission emission(FxDispatch.Command command) {
    return FxDispatch.emission(command, failureHandler);
  }

  private void dispatch(FxDispatch.Emission emission) {
    for (int v = 0; v < viewerCount; v++) {
      int viewerIndex = viewerIndices[v];
      if (viewersSnapshot.tryEmit(viewerIndex)) {
        viewersSnapshot.dispatch(viewerIndex, emission);
      }
    }
  }

  private int budget(int count) {
    if (!particlesOn || viewerCount <= 0 || count <= 0) {
      return 0;
    }

    double scalar = FxBudget.densityScalar(priority);
    double exact = count * scalar;
    int scaled = (int) exact;
    double frac = exact - scaled;
    if (frac > 0.0D && ThreadLocalRandom.current().nextDouble() < frac) {
      scaled++;
    }
    if (scaled <= 0) {
      if (priority != FxPriority.GAMEPLAY) {
        return 0;
      }
      scaled = 1;
    }

    int headroom = FxBudget.PER_EMITTER_PARTICLE_CAP - emittedParticles;
    if (headroom <= 0) {
      return 0;
    }

    scaled = Math.min(scaled, headroom);
    int grantedPackets = FxBudget.tryConsume(priority, scaled * viewerCount);
    int allowed = grantedPackets / viewerCount;
    if (allowed <= 0) {
      return 0;
    }

    emittedParticles += allowed;
    return allowed;
  }

  private static double[] circleLut(int points) {
    if (points <= 8) {
      return CIRCLE_8;
    }
    if (points <= 12) {
      return CIRCLE_12;
    }
    if (points <= 16) {
      return CIRCLE_16;
    }
    if (points <= 24) {
      return CIRCLE_24;
    }
    return CIRCLE_32;
  }

  private static double[] buildCircle(int points) {
    double[] lut = new double[points * 2];
    for (int i = 0; i < points; i++) {
      double angle = (TWO_PI * i) / points;
      lut[i << 1] = Math.cos(angle);
      lut[(i << 1) + 1] = Math.sin(angle);
    }
    return lut;
  }

  private static double[] buildDome(int points) {
    double[] lut = new double[points * 3];
    double golden = Math.PI * (3.0D - Math.sqrt(5.0D));
    for (int i = 0; i < points; i++) {
      double vertical = points == 1 ? 1.0D : (double) i / (points - 1);
      double ringRadius = Math.sqrt(Math.max(0.0D, 1.0D - (vertical * vertical)));
      double angle = golden * i;
      lut[i * 3] = Math.cos(angle) * ringRadius;
      lut[(i * 3) + 1] = vertical;
      lut[(i * 3) + 2] = Math.sin(angle) * ringRadius;
    }
    return lut;
  }
}
