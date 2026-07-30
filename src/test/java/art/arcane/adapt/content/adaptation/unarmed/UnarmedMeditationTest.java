package art.arcane.adapt.content.adaptation.unarmed;

import art.arcane.adapt.api.version.IAttribute;
import art.arcane.volmlib.util.collection.KList;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeModifier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UnarmedMeditationTest {
  @Test
  void tickDemandRetainsSessionAndCapacityCleanupWithoutLearners() {
    assertThat(UnarmedMeditation.requiresMaintenance(false, false, false)).isFalse();
    assertThat(UnarmedMeditation.requiresMaintenance(true, false, false)).isTrue();
    assertThat(UnarmedMeditation.requiresMaintenance(false, true, false)).isTrue();
    assertThat(UnarmedMeditation.requiresMaintenance(false, false, true)).isTrue();
  }

  @Test
  void installsOwnedTransientMaximumAbsorptionCapacity() {
    IAttribute attribute = mock(IAttribute.class);
    ArgumentCaptor<UUID> modifierId = ArgumentCaptor.forClass(UUID.class);
    ArgumentCaptor<NamespacedKey> modifierKey = ArgumentCaptor.forClass(NamespacedKey.class);
    when(attribute.getModifier(any(UUID.class), any(NamespacedKey.class))).thenReturn(new KList<>());

    boolean installed = UnarmedMeditation.ensureAbsorptionCapacity(attribute, 6D);

    assertThat(installed).isTrue();
    verify(attribute).setTransientModifier(
        modifierId.capture(),
        modifierKey.capture(),
        eq(6D),
        eq(AttributeModifier.Operation.ADD_NUMBER)
    );
    assertThat(modifierId.getValue()).isNotNull();
    assertThat(modifierKey.getValue().toString()).isEqualTo("adapt:unarmed-meditation");
    verify(attribute, never()).setBaseValue(anyDouble());
  }

  @Test
  void preservesMatchingCapacityWithoutReplacingTheModifier() {
    IAttribute attribute = mock(IAttribute.class);
    IAttribute.Modifier modifier = mock(IAttribute.Modifier.class);
    KList<IAttribute.Modifier> modifiers = new KList<>();
    modifiers.add(modifier);
    when(modifier.getAmount()).thenReturn(6D);
    when(modifier.getOperation()).thenReturn(AttributeModifier.Operation.ADD_NUMBER);
    when(attribute.getModifier(any(UUID.class), any(NamespacedKey.class))).thenReturn(modifiers);

    boolean installed = UnarmedMeditation.ensureAbsorptionCapacity(attribute, 6D);

    assertThat(installed).isTrue();
    verify(attribute, never()).setTransientModifier(
        any(UUID.class),
        any(NamespacedKey.class),
        anyDouble(),
        any(AttributeModifier.Operation.class)
    );
  }

  @Test
  void rejectsInvalidCapacityWithoutChangingAttributes() {
    IAttribute attribute = mock(IAttribute.class);

    assertThat(UnarmedMeditation.ensureAbsorptionCapacity(attribute, 0D)).isFalse();
    assertThat(UnarmedMeditation.ensureAbsorptionCapacity(attribute, Double.NaN)).isFalse();

    verifyNoInteractions(attribute);
  }

  @Test
  void cleanupRemovesOnlyMeditationsOwnedModifier() {
    IAttribute attribute = mock(IAttribute.class);
    ArgumentCaptor<UUID> modifierId = ArgumentCaptor.forClass(UUID.class);
    ArgumentCaptor<NamespacedKey> modifierKey = ArgumentCaptor.forClass(NamespacedKey.class);

    UnarmedMeditation.removeAbsorptionCapacity(attribute);

    verify(attribute).removeModifier(modifierId.capture(), modifierKey.capture());
    assertThat(modifierId.getValue()).isNotNull();
    assertThat(modifierKey.getValue().toString()).isEqualTo("adapt:unarmed-meditation");
    verify(attribute, never()).setBaseValue(anyDouble());
  }
}
