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

/** Paper 1.20.1 modifiers have no key: they carry a UUID and a "namespace-key" name instead. */
class RuntimeAttributeTest {
  @Test
  void transientModifierUsesNonPersistentAttributeApiWithAKeyDerivedName() {
    AttributeInstance instance = mock(AttributeInstance.class);
    when(instance.getModifiers()).thenReturn(Set.of());
    RuntimeAttribute attribute = new RuntimeAttribute(instance);
    UUID id = UUID.randomUUID();

    attribute.setTransientModifier(id, NamespacedKey.fromString("adapt:transient-test"), 0.25D, AttributeModifier.Operation.ADD_SCALAR);

    ArgumentCaptor<AttributeModifier> modifier = ArgumentCaptor.forClass(AttributeModifier.class);
    verify(instance).addTransientModifier(modifier.capture());
    verify(instance, never()).addModifier(any(AttributeModifier.class));
    assertThat(modifier.getValue().getUniqueId()).isEqualTo(id);
    assertThat(modifier.getValue().getName()).isEqualTo("adapt-transient-test");
    assertThat(modifier.getValue().getAmount()).isEqualTo(0.25D);
    assertThat(modifier.getValue().getOperation()).isEqualTo(AttributeModifier.Operation.ADD_SCALAR);
  }

  @Test
  void getAllModifiersMapsEveryModifierToTheRecordShapeWithoutAKey() {
    AttributeInstance instance = mock(AttributeInstance.class);
    UUID firstId = UUID.randomUUID();
    UUID secondId = UUID.randomUUID();
    AttributeModifier first = new AttributeModifier(firstId, "adapt-alpha", 0.5D, AttributeModifier.Operation.ADD_NUMBER);
    AttributeModifier second = new AttributeModifier(secondId, "other", 0.75D, AttributeModifier.Operation.ADD_SCALAR);
    when(instance.getModifiers()).thenReturn(List.of(first, second));
    RuntimeAttribute attribute = new RuntimeAttribute(instance);

    KList<IAttribute.Modifier> modifiers = attribute.getAllModifiers();

    assertThat(modifiers).containsExactlyInAnyOrder(
        new IAttribute.Modifier(firstId, null, 0.5D, AttributeModifier.Operation.ADD_NUMBER),
        new IAttribute.Modifier(secondId, null, 0.75D, AttributeModifier.Operation.ADD_SCALAR));
  }

  @Test
  void removeAllInNamespaceRemovesNamedModifiersAndReturnsCount() {
    AttributeInstance instance = mock(AttributeInstance.class);
    AttributeModifier first = new AttributeModifier(UUID.randomUUID(), "adapt-alpha", 0.5D, AttributeModifier.Operation.ADD_NUMBER);
    AttributeModifier second = new AttributeModifier(UUID.randomUUID(), "adapt-beta", 0.25D, AttributeModifier.Operation.ADD_SCALAR);
    AttributeModifier other = new AttributeModifier(UUID.randomUUID(), "minecraft-gamma", 1.0D, AttributeModifier.Operation.ADD_NUMBER);
    when(instance.getModifiers()).thenReturn(List.of(first, second, other));
    RuntimeAttribute attribute = new RuntimeAttribute(instance);

    int removed = attribute.removeAllInNamespace("adapt");

    assertThat(removed).isEqualTo(2);
    verify(instance).removeModifier(first);
    verify(instance).removeModifier(second);
    verify(instance, never()).removeModifier(other);
  }

  @Test
  void removeAllInNamespaceLeavesOtherNamespacesUntouched() {
    AttributeInstance instance = mock(AttributeInstance.class);
    AttributeModifier legacy = mock(AttributeModifier.class);
    when(legacy.getName()).thenReturn("otherplugin-boost");
    when(instance.getModifiers()).thenReturn(List.of(legacy));
    RuntimeAttribute attribute = new RuntimeAttribute(instance);

    int removed = attribute.removeAllInNamespace("adapt");

    assertThat(removed).isZero();
    verify(instance, never()).removeModifier(any(AttributeModifier.class));
  }
}
