package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;

import java.util.Optional;

public interface PlinthHost {
  int level(PlayerRef player, String adaptationId);

  boolean canStore(PlayerRef player, String adaptationId, Loc at);

  boolean canInfuse(PlayerRef player, String adaptationId, Loc at);

  Optional<PlinthCharge> chargeLevels(PlayerRef player, String adaptationId, int levels);

  FxEmitter fx(Loc at, FxPriority priority);

  FxTimeline timeline(Loc at);

  void infused(PlayerRef player, Infusion infusion, Loc at);
}
