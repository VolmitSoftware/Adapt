package art.arcane.adapt.api.fx;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FxTimeline {
  private static final long OWNER_DISPATCH_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5L);

  private final String label;
  private final boolean particlesOn;
  private final boolean soundsOn;
  private final Color color;
  private final Entity anchorEntity;
  private final Location fixedAnchor;
  private final AtomicBoolean advancePending = new AtomicBoolean();
  private final AtomicBoolean failureReported = new AtomicBoolean();
  private World world;
  private Location entityScratch;
  private double anchorX;
  private double anchorY;
  private double anchorZ;
  private int durationTicks = 20;
  private FxPriority priority = FxPriority.TRANSITION;
  private double cullRadius = FxViewers.DEFAULT_CULL_RADIUS;
  private Frame frame;
  private Runnable completion;
  private int tick;
  private volatile boolean cancelled;
  private volatile boolean finished;
  private volatile long pendingSinceNanos;
  private boolean started;

  private FxTimeline(String label, boolean particlesOn, boolean soundsOn, Color color, World world, double anchorX, double anchorY, double anchorZ, Entity anchorEntity) {
    this.label = label;
    this.particlesOn = particlesOn;
    this.soundsOn = soundsOn;
    this.color = color;
    this.world = world;
    this.anchorX = anchorX;
    this.anchorY = anchorY;
    this.anchorZ = anchorZ;
    this.anchorEntity = anchorEntity;
    this.fixedAnchor = anchorEntity == null && world != null ? new Location(world, anchorX, anchorY, anchorZ) : null;
  }

  public static FxTimeline at(Adaptation<?> adaptation, Location location) {
    if (adaptation == null || location == null || location.getWorld() == null) {
      return dead();
    }
    return new FxTimeline(adaptation.getName(), adaptation.areParticlesEnabled(), adaptation.areSoundsEnabled(), Fx.adaptationColor(adaptation), location.getWorld(), location.getX(), location.getY(), location.getZ(), null);
  }

  public static FxTimeline follow(Adaptation<?> adaptation, Entity entity) {
    if (adaptation == null || entity == null) {
      return dead();
    }
    return new FxTimeline(adaptation.getName(), adaptation.areParticlesEnabled(), adaptation.areSoundsEnabled(), Fx.adaptationColor(adaptation), null, 0.0D, 0.0D, 0.0D, entity);
  }

  public static FxTimeline at(Skill<?> skill, Location location) {
    if (skill == null || location == null || location.getWorld() == null) {
      return dead();
    }
    return new FxTimeline(skill.getName(), skill.areParticlesEnabled(), skill.areSoundsEnabled(), Fx.skillColor(skill), location.getWorld(), location.getX(), location.getY(), location.getZ(), null);
  }

  public static FxTimeline follow(Skill<?> skill, Entity entity) {
    if (skill == null || entity == null) {
      return dead();
    }
    return new FxTimeline(skill.getName(), skill.areParticlesEnabled(), skill.areSoundsEnabled(), Fx.skillColor(skill), null, 0.0D, 0.0D, 0.0D, entity);
  }

  public FxTimeline duration(int ticks) {
    this.durationTicks = Math.max(1, ticks);
    return this;
  }

  public FxTimeline priority(FxPriority priority) {
    if (priority != null) {
      this.priority = priority;
    }
    return this;
  }

  public FxTimeline cullRadius(double radius) {
    this.cullRadius = Math.min(FxViewers.MAX_CULL_RADIUS, Math.max(1.0D, radius));
    return this;
  }

  public FxTimeline frame(Frame frame) {
    this.frame = frame;
    return this;
  }

  public FxTimeline onComplete(Runnable completion) {
    this.completion = completion;
    return this;
  }

  public void start() {
    if (started || cancelled || frame == null || (fixedAnchor == null && anchorEntity == null)) {
      return;
    }

    started = true;
    FxDirector director = FxDirector.active();
    if (director != null) {
      director.register(this);
    }
  }

  public void cancel() {
    cancelled = true;
    finished = true;
  }

  boolean advance() {
    return advance(J.isFoliaThreading());
  }

  boolean advance(boolean foliaThreading) {
    if (cancelled || finished) {
      return false;
    }

    if (advancePending.get()) {
      long pendingFor = System.nanoTime() - pendingSinceNanos;
      if (pendingFor >= OWNER_DISPATCH_TIMEOUT_NANOS) {
        fail(new IllegalStateException("FX timeline owner dispatch timed out for '" + label + "'."));
      }
      return !cancelled && !finished;
    }

    if (!advancePending.compareAndSet(false, true)) {
      return true;
    }
    pendingSinceNanos = System.nanoTime();

    try {
      if (!foliaThreading) {
        advanceOwned();
        return !cancelled && !finished;
      }

      boolean accepted = anchorEntity == null
          ? J.runAt(fixedAnchor, this::advanceOwned)
          : J.runEntity(anchorEntity, this::advanceOwned);
      if (!accepted) {
        advancePending.set(false);
        fail(new IllegalStateException("FX timeline owner dispatch was rejected for '" + label + "'."));
      }
    } catch (Throwable error) {
      advancePending.set(false);
      fail(error);
    }
    return !cancelled && !finished;
  }

  void fail(Throwable error) {
    cancelled = true;
    finished = true;
    if (!failureReported.compareAndSet(false, true)) {
      return;
    }
    Adapt.error("FX timeline for '" + label + "' failed; cancelling it.");
    error.printStackTrace();
  }

  private void advanceOwned() {
    try {
      if (cancelled || finished) {
        return;
      }
      if (!renderFrame()) {
        finished = true;
      }
    } catch (Throwable error) {
      fail(error);
    } finally {
      advancePending.set(false);
    }
  }

  private boolean renderFrame() {
    if (anchorEntity != null && !updateEntityAnchor()) {
      cancelled = true;
      return false;
    }

    if (priority != FxPriority.GAMEPLAY && FxBudget.densityScalar(priority) <= 0.0D) {
      tick++;
      if (tick >= durationTicks) {
        complete();
        return false;
      }
      return true;
    }

    double progress = durationTicks <= 1 ? 1.0D : (double) tick / (durationTicks - 1);
    FxEmitter emitter = FxEmitter.create(world, anchorX, anchorY, anchorZ, priority, cullRadius, particlesOn, soundsOn, color, this::fail);
    frame.render(emitter, tick, progress);
    if (cancelled) {
      return false;
    }
    tick++;
    if (tick >= durationTicks) {
      complete();
      return false;
    }

    return true;
  }

  private boolean updateEntityAnchor() {
    if (!anchorEntity.isValid() || anchorEntity.isDead()) {
      return false;
    }

    Location location;
    if (entityScratch == null) {
      location = anchorEntity.getLocation();
      if (location == null) {
        return false;
      }
      entityScratch = location;
    } else {
      location = anchorEntity.getLocation(entityScratch);
    }

    World currentWorld = location.getWorld();
    if (currentWorld == null) {
      return false;
    }
    if (world == null) {
      world = currentWorld;
    } else if (currentWorld != world) {
      return false;
    }

    anchorX = location.getX();
    anchorY = location.getY();
    anchorZ = location.getZ();
    return true;
  }

  private void complete() {
    if (completion != null) {
      completion.run();
    }
  }

  String label() {
    return label;
  }

  FxPriority priority() {
    return priority;
  }

  private static FxTimeline dead() {
    FxTimeline timeline = new FxTimeline("invalid", false, false, Color.WHITE, null, 0, 0, 0, null);
    timeline.cancelled = true;
    return timeline;
  }

  @FunctionalInterface
  public interface Frame {
    void render(FxEmitter fx, int tick, double progress);
  }
}
