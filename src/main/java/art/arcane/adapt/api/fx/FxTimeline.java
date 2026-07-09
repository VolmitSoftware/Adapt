package art.arcane.adapt.api.fx;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

public final class FxTimeline {
  private final String label;
  private final boolean particlesOn;
  private final boolean soundsOn;
  private final Color color;
  private final World world;
  private final Entity anchorEntity;
  private final Location entityScratch;
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
    this.entityScratch = anchorEntity == null ? null : new Location(world, anchorX, anchorY, anchorZ);
  }

  public static FxTimeline at(Adaptation<?> adaptation, Location location) {
    if (adaptation == null || location == null || location.getWorld() == null) {
      return dead();
    }
    return new FxTimeline(adaptation.getName(), adaptation.areParticlesEnabled(), adaptation.areSoundsEnabled(), Fx.adaptationColor(adaptation), location.getWorld(), location.getX(), location.getY(), location.getZ(), null);
  }

  public static FxTimeline follow(Adaptation<?> adaptation, Entity entity) {
    if (adaptation == null || entity == null || entity.getWorld() == null) {
      return dead();
    }
    Location location = entity.getLocation();
    return new FxTimeline(adaptation.getName(), adaptation.areParticlesEnabled(), adaptation.areSoundsEnabled(), Fx.adaptationColor(adaptation), entity.getWorld(), location.getX(), location.getY(), location.getZ(), entity);
  }

  public static FxTimeline at(Skill<?> skill, Location location) {
    if (skill == null || location == null || location.getWorld() == null) {
      return dead();
    }
    return new FxTimeline(skill.getName(), skill.areParticlesEnabled(), skill.areSoundsEnabled(), Fx.skillColor(skill), location.getWorld(), location.getX(), location.getY(), location.getZ(), null);
  }

  public static FxTimeline follow(Skill<?> skill, Entity entity) {
    if (skill == null || entity == null || entity.getWorld() == null) {
      return dead();
    }
    Location location = entity.getLocation();
    return new FxTimeline(skill.getName(), skill.areParticlesEnabled(), skill.areSoundsEnabled(), Fx.skillColor(skill), entity.getWorld(), location.getX(), location.getY(), location.getZ(), entity);
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
    if (started || cancelled || frame == null || world == null) {
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
  }

  boolean advance() {
    if (cancelled) {
      return false;
    }

    if (anchorEntity != null) {
      if (!anchorEntity.isValid() || anchorEntity.isDead()) {
        cancelled = true;
        return false;
      }

      Location location = anchorEntity.getLocation(entityScratch);
      if (location.getWorld() != world) {
        cancelled = true;
        return false;
      }

      anchorX = location.getX();
      anchorY = location.getY();
      anchorZ = location.getZ();
    }

    if (priority != FxPriority.GAMEPLAY && FxBudget.densityScalar(priority) <= 0.0D) {
      tick++;
      if (tick >= durationTicks) {
        if (completion != null) {
          completion.run();
        }
        return false;
      }
      return true;
    }

    double progress = durationTicks <= 1 ? 1.0D : (double) tick / (durationTicks - 1);
    FxEmitter emitter = FxEmitter.create(world, anchorX, anchorY, anchorZ, priority, cullRadius, particlesOn, soundsOn, color);
    frame.render(emitter, tick, progress);
    tick++;
    if (tick >= durationTicks) {
      if (completion != null) {
        completion.run();
      }
      return false;
    }

    return true;
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
