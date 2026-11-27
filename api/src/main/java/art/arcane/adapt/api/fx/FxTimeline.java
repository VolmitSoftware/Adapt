package art.arcane.adapt.api.fx;

import art.arcane.adapt.api.content.ContentComponent;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.ApiStatus;

/**
 * An effect drawn over several ticks: each tick Adapt hands {@link Frame#render} a fresh emitter at the anchor.
 * Configure it, then {@link #start()}; nothing runs before. A timeline that follows an entity moves with it and ends
 * when the entity is gone.
 */
@ApiStatus.Experimental
public interface FxTimeline {
  /** A timeline anchored at {@code location}, owned by {@code owner} for its effect settings and colour. */
  static FxTimeline at(ContentComponent owner, Loc location) {
    return Engine.bridge().fx().at(owner, location);
  }

  /** A timeline anchored on {@code entity}, following it. */
  static FxTimeline follow(ContentComponent owner, EntityRef entity) {
    return Engine.bridge().fx().follow(owner, entity);
  }

  /** How many ticks the timeline runs; at least 1, 20 by default. */
  FxTimeline duration(int ticks);

  /** The budget class of every frame; {@link FxPriority#TRANSITION} by default. */
  FxTimeline priority(FxPriority priority);

  /** How far away players still see the effect, in blocks. */
  FxTimeline cullRadius(double radius);

  /** The code that draws each tick; required before {@link #start()}. */
  FxTimeline frame(Frame frame);

  /** Runs once after the last frame, also when the timeline ends early. */
  FxTimeline onComplete(Runnable completion);

  /** Starts the timeline; a second call, or one without a frame, does nothing. */
  void start();

  /** Stops the timeline; no further frame is drawn. */
  void cancel();

  /** Draws one tick of a timeline. */
  @FunctionalInterface
  interface Frame {
    /**
     * @param tick     the tick, from 0
     * @param progress {@code tick} as a fraction of the duration, from 0 to 1
     */
    void render(FxEmitter fx, int tick, double progress);
  }
}
