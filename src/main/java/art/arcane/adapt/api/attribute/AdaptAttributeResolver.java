package art.arcane.adapt.api.attribute;

import art.arcane.adapt.api.version.IAttribute;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;

@FunctionalInterface
public interface AdaptAttributeResolver {
  IAttribute resolve(LivingEntity entity, Attribute attribute);
}
