package art.arcane.adapt.api.golem;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

final class FakeAttribute {
  final List<AttributeModifier> modifiers = new ArrayList<>();
  final AttributeInstance instance = mock(AttributeInstance.class);
  final double base;

  FakeAttribute(double base) {
    this.base = base;
    lenient().when(instance.getModifiers()).thenAnswer(invocation -> List.copyOf(modifiers));
    lenient().doAnswer(invocation -> modifiers.add(invocation.getArgument(0))).when(instance).addModifier(any(AttributeModifier.class));
    lenient().doAnswer(invocation -> modifiers.remove(invocation.<AttributeModifier>getArgument(0))).when(instance).removeModifier(any(AttributeModifier.class));
    lenient().when(instance.getBaseValue()).thenAnswer(invocation -> base);
    lenient().when(instance.getValue()).thenAnswer(invocation -> value());
  }

  double value() {
    double added = base;
    for (AttributeModifier modifier : modifiers) {
      if (modifier.getOperation() == AttributeModifier.Operation.ADD_NUMBER) {
        added += modifier.getAmount();
      }
    }
    double value = added;
    for (AttributeModifier modifier : modifiers) {
      if (modifier.getOperation() == AttributeModifier.Operation.ADD_SCALAR) {
        value += added * modifier.getAmount();
      }
    }
    for (AttributeModifier modifier : modifiers) {
      if (modifier.getOperation() == AttributeModifier.Operation.MULTIPLY_SCALAR_1) {
        value *= 1D + modifier.getAmount();
      }
    }
    return value;
  }

  Optional<AttributeModifier> modifier(NamespacedKey key) {
    for (AttributeModifier modifier : modifiers) {
      if (key.equals(modifier.getKey())) {
        return Optional.of(modifier);
      }
    }
    return Optional.empty();
  }
}
