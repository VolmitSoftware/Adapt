package art.arcane.adapt.api.fx;

import art.arcane.adapt.util.reflect.registries.Particles;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;

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

  private final World world;
  private final double x;
  private final double y;
  private final double z;
  private final FxPriority priority;
  private final boolean particlesOn;
  private final boolean soundsOn;
  private final Color color;
  private final FxViewers.Snapshot viewersSnapshot;
  private final int[] viewerIndices;
  private final int viewerCount;
  private final Consumer<Throwable> failureHandler;
  private int emittedParticles;

  private FxEmitter() {
    this.world = null;
    this.x = 0;
    this.y = 0;
    this.z = 0;
    this.priority = FxPriority.AMBIENT;
    this.particlesOn = false;
    this.soundsOn = false;
    this.color = Color.WHITE;
    this.viewersSnapshot = null;
    this.viewerIndices = new int[0];
    this.viewerCount = 0;
    this.failureHandler = null;
  }

  private FxEmitter(World world, double x, double y, double z, FxPriority priority, boolean particlesOn, boolean soundsOn, Color color, FxViewers.Snapshot viewersSnapshot, int[] viewerIndices, int viewerCount, Consumer<Throwable> failureHandler) {
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

  static FxEmitter create(World world, double x, double y, double z, FxPriority priority, double cullRadius, boolean particles, boolean sounds, Color color) {
    return create(world, x, y, z, priority, cullRadius, particles, sounds, color, null);
  }

  static FxEmitter create(World world, double x, double y, double z, FxPriority priority, double cullRadius, boolean particles, boolean sounds, Color color, Consumer<Throwable> failureHandler) {
    if (world == null || priority == null || (!particles && !sounds)) {
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

    return new FxEmitter(world, x, y, z, priority, particles, sounds, color == null ? Color.WHITE : color, current, indices, filled, failureHandler);
  }

  public FxEmitter particle(Particle particle, int count, double ox, double oy, double oz, double spread, double speed) {
    return particle(particle, count, ox, oy, oz, spread, speed, null);
  }

  public <T> FxEmitter particle(Particle particle, int count, double ox, double oy, double oz, double spread, double speed, T data) {
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
    dispatch(emission(player -> player.spawnParticle(particle, px, py, pz, allowed, spread, spread, spread, speed, data)));
    return this;
  }

  public FxEmitter ring(Particle particle, double radius, int points, double yOffset) {
    return ring(particle, radius, points, yOffset, null);
  }

  public <T> FxEmitter ring(Particle particle, double radius, int points, double yOffset, T data) {
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
    dispatch(emission(player -> {
      for (int k = 0; k < allowed; k++) {
        int i = (int) (((long) k * total) / allowed);
        player.spawnParticle(particle, x + (lut[i << 1] * radius), py, z + (lut[(i << 1) + 1] * radius), 1, 0, 0, 0, 0, data);
      }
    }));
    return this;
  }

  public FxEmitter arc(Particle particle, double radius, int points, double yawRadians, double spanRadians, double yOffset) {
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
        player.spawnParticle(particle, x + ((cosYaw * lx) - (sinYaw * lz)), py, z + ((sinYaw * lx) + (cosYaw * lz)), 1, 0, 0, 0, 0, null);
      }
    }));
    return this;
  }

  public FxEmitter helix(Particle particle, double radius, double height, int points, double phaseRadians) {
    return helix(particle, radius, height, points, phaseRadians, null);
  }

  public <T> FxEmitter helix(Particle particle, double radius, double height, int points, double phaseRadians, T data) {
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
    dispatch(emission(player -> {
      for (int k = 0; k < allowed; k++) {
        double t = allowed == 1 ? 0.5D : (double) k / (allowed - 1);
        int i = (int) (((long) k * total * HELIX_REVOLUTIONS) / allowed) % total;
        double lx = CIRCLE_32[i << 1] * radius;
        double lz = CIRCLE_32[(i << 1) + 1] * radius;
        player.spawnParticle(particle, x + ((cosPhase * lx) - (sinPhase * lz)), y + (t * height), z + ((sinPhase * lx) + (cosPhase * lz)), 1, 0, 0, 0, 0, data);
      }
    }));
    return this;
  }

  public FxEmitter line(Particle particle, double tx, double ty, double tz, int points) {
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
    dispatch(emission(player -> {
      for (int k = 0; k < allowed; k++) {
        double t = allowed == 1 ? 0.5D : (double) k / (allowed - 1);
        player.spawnParticle(particle, x + (dx * t), y + (dy * t), z + (dz * t), 1, 0, 0, 0, 0, null);
      }
    }));
    return this;
  }

  public FxEmitter burst(Particle particle, int count, double spread) {
    if (particle == null) {
      return this;
    }

    int allowed = budget(count);
    if (allowed <= 0) {
      return this;
    }

    dispatch(emission(player -> player.spawnParticle(particle, x, y, z, allowed, spread, spread, spread, 0, null)));
    return this;
  }

  public FxEmitter column(Particle particle, int count, double height) {
    if (particle == null) {
      return this;
    }

    int allowed = budget(count);
    if (allowed <= 0) {
      return this;
    }

    dispatch(emission(player -> {
      for (int k = 0; k < allowed; k++) {
        double t = allowed == 1 ? 0.5D : (double) k / (allowed - 1);
        player.spawnParticle(particle, x, y + (t * height), z, 1, 0, 0, 0, 0, null);
      }
    }));
    return this;
  }

  public FxEmitter dome(Particle particle, double radius, int points) {
    if (particle == null) {
      return this;
    }

    int total = DOME_48.length / 3;
    int allowed = budget(Math.min(Math.max(1, points), total));
    if (allowed <= 0) {
      return this;
    }

    dispatch(emission(player -> {
      for (int k = 0; k < allowed; k++) {
        int base = ((int) (((long) k * total) / allowed)) * 3;
        player.spawnParticle(particle, x + (DOME_48[base] * radius), y + (DOME_48[base + 1] * radius), z + (DOME_48[base + 2] * radius), 1, 0, 0, 0, 0, null);
      }
    }));
    return this;
  }

  public FxEmitter trail(Particle particle, double dirX, double dirY, double dirZ, double length, int points) {
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
    dispatch(emission(player -> {
      for (int k = 0; k < allowed; k++) {
        double t = (double) (k + 1) / allowed;
        player.spawnParticle(particle, x + (sx * t), y + (sy * t), z + (sz * t), 1, 0, 0, 0, 0, null);
      }
    }));
    return this;
  }

  public FxEmitter dustRing(double radius, int points, float size) {
    return dustRing(color, radius, points, size);
  }

  public FxEmitter dustRing(Color customColor, double radius, int points, float size) {
    if (!particlesOn) {
      return this;
    }
    return ring(Particles.REDSTONE, radius, points, 0, new Particle.DustOptions(customColor == null ? color : customColor, size));
  }

  public FxEmitter dustBurst(int count, double spread, float size) {
    return dustBurst(color, count, spread, size);
  }

  public FxEmitter dustBurst(Color customColor, int count, double spread, float size) {
    if (!particlesOn) {
      return this;
    }
    return particle(Particles.REDSTONE, count, 0, 0, 0, spread, 0, new Particle.DustOptions(customColor == null ? color : customColor, size));
  }

  public FxEmitter dustHelix(double radius, double height, int points, double phaseRadians, float size) {
    return dustHelix(color, radius, height, points, phaseRadians, size);
  }

  public FxEmitter dustHelix(Color customColor, double radius, double height, int points, double phaseRadians, float size) {
    if (!particlesOn) {
      return this;
    }
    return helix(Particles.REDSTONE, radius, height, points, phaseRadians, new Particle.DustOptions(customColor == null ? color : customColor, size));
  }

  public FxEmitter sound(Sound sound, float volume, float pitch) {
    if (soundShed()) {
      return this;
    }
    emitSound(sound, volume, pitch);
    return this;
  }

  public FxEmitter chord(Sound a, float va, float pa, Sound b, float vb, float pb) {
    if (soundShed()) {
      return this;
    }
    emitSound(a, va, pa);
    emitSound(b, vb, pb);
    return this;
  }

  public FxEmitter chord(Sound a, float va, float pa, Sound b, float vb, float pb, Sound c, float vc, float pc) {
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

  private void emitSound(Sound sound, float volume, float pitch) {
    if (sound == null) {
      return;
    }

    double radius = Math.min(48.0D, Math.max(8.0D, 16.0D * volume));
    Location location = new Location(world, x, y, z);
    viewersSnapshot.dispatch(world, x, y, z, radius, emission(player -> player.playSound(location, sound, volume, pitch)));
  }

  public Color color() {
    return color;
  }

  public int viewerCount() {
    return viewerCount;
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
