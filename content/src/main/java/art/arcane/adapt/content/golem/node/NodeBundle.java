package art.arcane.adapt.content.golem.node;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.display.ItemDisplayRef;
import art.arcane.adapt.platform.entity.display.TextDisplayRef;

import java.util.List;

public record NodeBundle(ItemDisplayRef icon, EntityRef hitbox, TextDisplayRef label) {
  public List<EntityRef> entities() {
    return List.of(icon, hitbox, label);
  }
}
