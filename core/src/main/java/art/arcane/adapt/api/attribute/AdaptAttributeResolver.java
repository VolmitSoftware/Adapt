package art.arcane.adapt.api.attribute;

import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.value.Id;

/**
 * Attribute access for {@link AdaptAttributeService}. {@link #LIVING_REF} goes through the SPI; a platform that can
 * list modifiers installs its own through {@code AdaptPorts} so {@link #removeAllInNamespace} also clears modifiers
 * left over from earlier sessions.
 */
public interface AdaptAttributeResolver {
  AdaptAttributeResolver LIVING_REF = new AdaptAttributeResolver() {
    @Override
    public boolean setTransientModifier(LivingRef entity, Id attribute, AttributeMod modifier) {
      return entity.setTransientModifier(attribute, modifier);
    }

    @Override
    public void removeModifier(LivingRef entity, Id attribute, Id key) {
      if (entity.hasAttribute(attribute)) {
        entity.removeModifier(attribute, key);
      }
    }

    @Override
    public int removeAllInNamespace(LivingRef entity, Id attribute, String namespace) {
      return 0;
    }
  };

  /** Returns false when the entity lacks the attribute. */
  boolean setTransientModifier(LivingRef entity, Id attribute, AttributeMod modifier);

  void removeModifier(LivingRef entity, Id attribute, Id key);

  /** Removes every modifier on the attribute whose key has this namespace; returns how many were removed. */
  int removeAllInNamespace(LivingRef entity, Id attribute, String namespace);
}
