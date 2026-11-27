package art.arcane.adapt.api.internal;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.ContentComponent;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.ApiStatus;

/** Effects for an owner given by the caller; an owner the engine did not build draws nothing. */
@ApiStatus.Internal
public interface FxRuntime {
  FxEmitter now(ContentComponent owner, Loc location, FxPriority priority);

  FxEmitter now(ContentComponent owner, EntityRef entity, FxPriority priority);

  FxTimeline at(ContentComponent owner, Loc location);

  FxTimeline follow(ContentComponent owner, EntityRef entity);

  void sound(Adaptation<?> owner, PlayerRef viewer, Loc location, Id sound, float volume, float pitch);
}
