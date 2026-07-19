package art.arcane.adapt.api.version;

import art.arcane.volmlib.util.collection.KList;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeAttributeTest {
  @Test
  void transientModifierUsesNonPersistentAttributeApi() {
    AttributeInstance instance = mock(AttributeInstance.class);
    when(instance.getModifiers()).thenReturn(Set.of());
    RuntimeAttribute attribute = new RuntimeAttribute(instance);
    NamespacedKey key = NamespacedKey.fromString("adapt:transient-test");

    attribute.setTransientModifier(UUID.randomUUID(), key, 0.25D, AttributeModifier.Operation.ADD_SCALAR);

    ArgumentCaptor<AttributeModifier> modifier = ArgumentCaptor.forClass(AttributeModifier.class);
    verify(instance).addTransientModifier(modifier.capture());
    verify(instance, never()).addModifier(any(AttributeModifier.class));
    assertThat(modifier.getValue().getAmount()).isEqualTo(0.25D);
    assertThat(modifier.getValue().getOperation()).isEqualTo(AttributeModifier.Operation.ADD_SCALAR);
  }

  @Test
  void getAllModifiersMapsEveryModifierToTheRecordShape() {
    AttributeInstance instance = mock(AttributeInstance.class);
    NamespacedKey keyedKey = NamespacedKey.fromString("adapt:alpha");
    AttributeModifier keyed = new AttributeModifier(keyedKey, 0.5D, AttributeModifier.Operation.ADD_NUMBER);
    UUID legacyId = UUID.randomUUID();
    AttributeModifier legacy = mock(AttributeModifier.class);
    when(legacy.getKey()).thenReturn(null);
    when(legacy.getUniqueId()).thenReturn(legacyId);
    when(legacy.getAmount()).thenReturn(0.75D);
    when(legacy.getOperation()).thenReturn(AttributeModifier.Operation.ADD_SCALAR);
    when(instance.getModifiers()).thenReturn(List.of(keyed, legacy));
    RuntimeAttribute attribute = new RuntimeAttribute(instance);

    KList<IAttribute.Modifier> modifiers = attribute.getAllModifiers();

    assertThat(modifiers).containsExactlyInAnyOrder(
        new IAttribute.Modifier(keyed.getUniqueId(), keyedKey, 0.5D, AttributeModifier.Operation.ADD_NUMBER),
        new IAttribute.Modifier(legacyId, null, 0.75D, AttributeModifier.Operation.ADD_SCALAR));
  }

  @Test
  void removeAllInNamespaceRemovesModernKeyModifiersAndReturnsCount() {
    AttributeInstance instance = mock(AttributeInstance.class);
    AttributeModifier first = new AttributeModifier(NamespacedKey.fromString("adapt:alpha"), 0.5D, AttributeModifier.Operation.ADD_NUMBER);
    AttributeModifier second = new AttributeModifier(NamespacedKey.fromString("adapt:beta"), 0.25D, AttributeModifier.Operation.ADD_SCALAR);
    AttributeModifier other = new AttributeModifier(NamespacedKey.fromString("minecraft:gamma"), 1.0D, AttributeModifier.Operation.ADD_NUMBER);
    when(instance.getModifiers()).thenReturn(List.of(first, second, other));
    RuntimeAttribute attribute = new RuntimeAttribute(instance);

    int removed = attribute.removeAllInNamespace("adapt");

    assertThat(removed).isEqualTo(2);
    verify(instance).removeModifier(first);
    verify(instance).removeModifier(second);
    verify(instance, never()).removeModifier(other);
  }

  @Test
  void removeAllInNamespaceRemovesLegacyNamedModifiers() {
    AttributeInstance instance = mock(AttributeInstance.class);
    AttributeModifier legacy = mock(AttributeModifier.class);
    when(legacy.getKey()).thenReturn(null);
    when(legacy.getName()).thenReturn("adapt-legacy-boost");
    when(instance.getModifiers()).thenReturn(List.of(legacy));
    RuntimeAttribute attribute = new RuntimeAttribute(instance);

    int removed = attribute.removeAllInNamespace("adapt");

    assertThat(removed).isEqualTo(1);
    verify(instance).removeModifier(legacy);
  }

  @Test
  void removeAllInNamespaceLeavesOtherNamespacesUntouched() {
    AttributeInstance instance = mock(AttributeInstance.class);
    AttributeModifier modern = new AttributeModifier(NamespacedKey.fromString("minecraft:gamma"), 1.0D, AttributeModifier.Operation.ADD_NUMBER);
    AttributeModifier legacy = mock(AttributeModifier.class);
    when(legacy.getKey()).thenReturn(null);
    when(legacy.getName()).thenReturn("otherplugin-boost");
    when(instance.getModifiers()).thenReturn(List.of(modern, legacy));
    RuntimeAttribute attribute = new RuntimeAttribute(instance);

    int removed = attribute.removeAllInNamespace("adapt");

    assertThat(removed).isZero();
    verify(instance, never()).removeModifier(any(AttributeModifier.class));
  }
}
