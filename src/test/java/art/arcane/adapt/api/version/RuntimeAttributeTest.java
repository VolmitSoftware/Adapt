package art.arcane.adapt.api.version;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
}
