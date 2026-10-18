package art.arcane.adapt.api.golem;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface GolemBindListener {
  void bound(Player owner, GolemHandle handle);
}
