package art.arcane.adapt.engine.fx;

import art.arcane.adapt.api.content.ContentComponent;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FxTimeline implements art.arcane.adapt.api.fx.FxTimeline {
  private static final long OWNER_DISPATCH_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5L);

  private final String label;
  private final boolean particlesOn;
  private final boolean soundsOn;
  private final Rgb color;
  private final EntityRef anchorEntity;
  private final Loc fixedAnchor;
  private final AtomicBoolean advancePending = new AtomicBoolean();
  private final AtomicBoolean failureReported = new AtomicBoolean();
  private WorldRef world;
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

  private FxTimeline(String label, boolean particlesOn, boolean soundsOn, Rgb color, WorldRef world, double anchorX, double anchorY, double anchorZ, EntityRef anchorEntity) {
    this.label = label;
    this.particlesOn = particlesOn;
    this.soundsOn = soundsOn;
    this.color = color;
    this.world = world;
    this.anchorX = anchorX;
    this.anchorY = anchorY;
    this.anchorZ = anchorZ;
    this.anchorEntity = anchorEntity;
    this.fixedAnchor = anchorEntity == null && world != null ? new Loc(world, anchorX, anchorY, anchorZ) : null;
  }

  public static FxTimeline at(ContentComponent owner, Loc location) {
    if (owner == null || location == null) {
      return dead();
    }
    return new FxTimeline(key(owner), owner.areParticlesEnabled(), owner.areSoundsEnabled(), Fx.color(owner), location.world(), location.x(), location.y(), location.z(), null);
  }

  public static FxTimeline follow(ContentComponent owner, EntityRef entity) {
    if (owner == null || entity == null) {
      return dead();
    }
    return new FxTimeline(key(owner), owner.areParticlesEnabled(), owner.areSoundsEnabled(), Fx.color(owner), null, 0.0D, 0.0D, 0.0D, entity);
  }

  private static String key(ContentComponent owner) {
    return owner instanceof Skill<?> skill ? SkillHost.lineKey(skill) : owner.getId().toString();
  }

  @Override
  public FxTimeline duration(int ticks) {
    this.durationTicks = Math.max(1, ticks);
    return this;
  }

  @Override
  public FxTimeline priority(FxPriority priority) {
    if (priority != null) {
      this.priority = priority;
    }
    return this;
  }

  @Override
  public FxTimeline cullRadius(double radius) {
    this.cullRadius = Math.min(FxViewers.MAX_CULL_RADIUS, Math.max(1.0D, radius));
    return this;
  }

  @Override
  public FxTimeline frame(Frame frame) {
    this.frame = frame;
    return this;
  }

  @Override
  public FxTimeline onComplete(Runnable completion) {
    this.completion = completion;
    return this;
  }

  @Override
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

  @Override
  public void cancel() {
    cancelled = true;
    finished = true;
  }

  boolean advance() {
    return advance(Tasks.isFoliaThreading());
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
          ? Tasks.runAt(fixedAnchor, this::advanceOwned)
          : Tasks.runEntity(anchorEntity, this::advanceOwned, this::anchorRetired);
      if (!accepted) {
        advancePending.set(false);
        if (anchorEntity != null) {
          cancelled = true;
          return false;
        }
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
    AdaptLog.error("FX timeline for '" + label + "' failed; cancelling it.");
    AdaptLog.error(error);
  }

  private void anchorRetired() {
    cancelled = true;
    advancePending.set(false);
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
    FxEmitter emitter = WorldEmitter.create(world, anchorX, anchorY, anchorZ, priority, cullRadius, particlesOn, soundsOn, color, this::fail);
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

    Loc location = anchorEntity.location();
    if (location == null) {
      return false;
    }

    WorldRef currentWorld = location.world();
    if (world == null) {
      world = currentWorld;
    } else if (!currentWorld.equals(world)) {
      return false;
    }

    anchorX = location.x();
    anchorY = location.y();
    anchorZ = location.z();
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
    FxTimeline timeline = new FxTimeline("invalid", false, false, Fx.WHITE, null, 0, 0, 0, null);
    timeline.cancelled = true;
    return timeline;
  }
}
