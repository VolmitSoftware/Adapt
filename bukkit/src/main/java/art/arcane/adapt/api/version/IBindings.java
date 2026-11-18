package art.arcane.adapt.api.version;

import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public interface IBindings extends Listener {

  IAttribute getAttribute(Attributable attributable, Attribute modifier);

  @Unmodifiable
  List<EntityType> getInvalidDamageableEntities();
}
