package art.arcane.adapt.api.golem;

import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Objects;

interface GolemAttribute {
  static GolemAttribute of(LivingRef entity, Id attribute) {
    return entity == null || attribute == null || !entity.hasAttribute(attribute) ? null : new Live(entity, attribute);
  }

  double value();

  List<AttributeMod> modifiers();

  void add(AttributeMod modifier);

  void addTransient(AttributeMod modifier);

  void remove(Id key);

  record Live(LivingRef entity, Id attribute) implements GolemAttribute {
    public Live {
      Objects.requireNonNull(entity);
      Objects.requireNonNull(attribute);
    }

    @Override
    public double value() {
      return entity.attributeValue(attribute);
    }

    @Override
    public List<AttributeMod> modifiers() {
      return entity.modifiers(attribute);
    }

    @Override
    public void add(AttributeMod modifier) {
      entity.setModifier(attribute, modifier);
    }

    @Override
    public void addTransient(AttributeMod modifier) {
      entity.setTransientModifier(attribute, modifier);
    }

    @Override
    public void remove(Id key) {
      entity.removeModifier(attribute, key);
    }
  }
}
