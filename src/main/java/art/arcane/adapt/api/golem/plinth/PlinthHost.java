package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;

public interface PlinthHost {
  int level(Player player, String adaptationId);

  boolean canStore(Player player, String adaptationId, Location at);

  boolean canInfuse(Player player, String adaptationId, Location at);

  Optional<PlinthCharge> chargeLevels(Player player, String adaptationId, int levels);

  FxEmitter fx(Location at, FxPriority priority);

  void infused(Player player, Infusion infusion, Location at);
}
