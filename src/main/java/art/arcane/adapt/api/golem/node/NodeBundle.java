package art.arcane.adapt.api.golem.node;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;

import java.util.List;

public record NodeBundle(ItemDisplay icon, Interaction hitbox, TextDisplay label) {
  public List<Entity> entities() {
    return List.of(icon, hitbox, label);
  }
}
