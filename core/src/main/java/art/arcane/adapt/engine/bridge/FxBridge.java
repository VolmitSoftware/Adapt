package art.arcane.adapt.engine.bridge;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.ContentComponent;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.internal.FxRuntime;
import art.arcane.adapt.engine.fx.Fx;
import art.arcane.adapt.engine.fx.FxTimeline;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

/** {@link FxRuntime} over the engine's {@link Fx} and {@link FxTimeline}. */
final class FxBridge implements FxRuntime {
  @Override
  public FxEmitter now(ContentComponent owner, Loc location, FxPriority priority) {
    return Fx.now(owner, location, priority);
  }

  @Override
  public FxEmitter now(ContentComponent owner, EntityRef entity, FxPriority priority) {
    return Fx.now(owner, entity, priority);
  }

  @Override
  public art.arcane.adapt.api.fx.FxTimeline at(ContentComponent owner, Loc location) {
    return FxTimeline.at(owner, location);
  }

  @Override
  public art.arcane.adapt.api.fx.FxTimeline follow(ContentComponent owner, EntityRef entity) {
    return FxTimeline.follow(owner, entity);
  }

  @Override
  public void sound(Adaptation<?> owner, PlayerRef viewer, Loc location, Id sound,
                    float volume, float pitch) {
    Fx.sound(owner, viewer, location, sound, volume, pitch);
  }
}
