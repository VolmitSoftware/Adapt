package art.arcane.adapt.content.golem;

import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class FakeAttribute implements GolemAttribute {
  final List<AttributeMod> modifiers;
  final double base;
  int adds;
  int transientAdds;
  int removals;

  FakeAttribute(double base) {
    this(base, new ArrayList<>());
  }

  FakeAttribute(double base, List<AttributeMod> modifiers) {
    this.base = base;
    this.modifiers = modifiers;
  }

  @Override
  public double value() {
    double added = base;
    for (AttributeMod modifier : modifiers) {
      if (modifier.operation() == AttributeOperation.ADD_VALUE) {
        added += modifier.amount();
      }
    }
    double value = added;
    for (AttributeMod modifier : modifiers) {
      if (modifier.operation() == AttributeOperation.ADD_MULTIPLIED_BASE) {
        value += added * modifier.amount();
      }
    }
    for (AttributeMod modifier : modifiers) {
      if (modifier.operation() == AttributeOperation.ADD_MULTIPLIED_TOTAL) {
        value *= 1D + modifier.amount();
      }
    }
    return value;
  }

  @Override
  public List<AttributeMod> modifiers() {
    return List.copyOf(modifiers);
  }

  @Override
  public void add(AttributeMod modifier) {
    adds++;
    put(modifier);
  }

  @Override
  public void addTransient(AttributeMod modifier) {
    transientAdds++;
    put(modifier);
  }

  @Override
  public void remove(Id key) {
    removals++;
    modifiers.removeIf(modifier -> modifier.key().equals(key));
  }

  private void put(AttributeMod modifier) {
    modifiers.removeIf(existing -> existing.key().equals(modifier.key()));
    modifiers.add(modifier);
  }

  Optional<AttributeMod> modifier(Id key) {
    for (AttributeMod modifier : modifiers) {
      if (key.equals(modifier.key())) {
        return Optional.of(modifier);
      }
    }
    return Optional.empty();
  }
}
