package art.arcane.adapt.content.adaptation.unarmed;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UnarmedMeditationTest extends AdaptTestBase {
  @Test
  void unarmedManualMeditationCannotPulseBeforeMaintenance() {
    UnarmedMeditation adaptation = new UnarmedMeditation() {
      @Override
      public boolean preferenceEnabled(PlayerRef player, PlayerPreference<CommonPreferences.Toggle> preference) {
        return preference == UnarmedPreferences.MANUAL;
      }
    };
    PlayerRef player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(UUID.randomUUID());
    when(player.location()).thenReturn(new Loc(mock(WorldRef.class), 0, 64, 0));
    when(player.isOnline()).thenReturn(true);
    when(player.isSneaking()).thenReturn(true);
    try (MockedStatic<Tasks> scheduler = mockStatic(Tasks.class)) {
      scheduler.when(() -> Tasks.runEntity(eq(player), any(Runnable.class), eq(20))).thenReturn(true);
      adaptation.on(new PlayerToggleSneakEvent(player, true));
      ArgumentCaptor<Runnable> pulse = ArgumentCaptor.forClass(Runnable.class);
      scheduler.verify(() -> Tasks.runEntity(eq(player), pulse.capture(), eq(20)));
      pulse.getValue().run();
      verify(player, never()).inventory();
      verify(player, never()).setAbsorption(anyDouble());
      scheduler.verify(() -> Tasks.runEntity(eq(player), any(Runnable.class), eq(20)));
    }
  }

  @Test
  void tickDemandRetainsSessionAndCapacityCleanupWithoutLearners() {
    assertThat(UnarmedMeditation.requiresMaintenance(false, false, false)).isFalse();
    assertThat(UnarmedMeditation.requiresMaintenance(true, false, false)).isTrue();
    assertThat(UnarmedMeditation.requiresMaintenance(false, true, false)).isTrue();
    assertThat(UnarmedMeditation.requiresMaintenance(false, false, true)).isTrue();
  }

  @Test
  void installsOwnedTransientMaximumAbsorptionCapacity() {
    LivingRef target = mock(LivingRef.class);
    ArgumentCaptor<AttributeMod> modifier = ArgumentCaptor.forClass(AttributeMod.class);
    when(target.hasAttribute(Attributes.MAX_ABSORPTION)).thenReturn(true);

    boolean installed = UnarmedMeditation.ensureAbsorptionCapacity(target, 6D);

    assertThat(installed).isTrue();
    verify(target).setTransientModifier(eq(Attributes.MAX_ABSORPTION), modifier.capture());
    assertThat(modifier.getValue().amount()).isEqualTo(6D);
    assertThat(modifier.getValue().operation()).isEqualTo(AttributeOperation.ADD_VALUE);
    assertThat(modifier.getValue().key().toString()).isEqualTo("adapt:unarmed-meditation");
    verify(target, never()).setAttributeBase(any(Id.class), anyDouble());
  }

  @Test
  void preservesMatchingCapacityWithoutReplacingTheModifier() {
    LivingRef target = mock(LivingRef.class);
    when(target.hasAttribute(Attributes.MAX_ABSORPTION)).thenReturn(true);
    when(target.modifier(any(Id.class), any(Id.class)))
        .thenReturn(new AttributeMod(UnarmedMeditation.ABSORPTION_MODIFIER_KEY, 6D, AttributeOperation.ADD_VALUE));

    boolean installed = UnarmedMeditation.ensureAbsorptionCapacity(target, 6D);

    assertThat(installed).isTrue();
    verify(target, never()).setTransientModifier(any(Id.class), any(AttributeMod.class));
  }

  @Test
  void replacesLegacyUuidCapacityInsteadOfStacking() {
    LivingRef target = mock(LivingRef.class);
    Id legacy = Id.minecraft(UUID.nameUUIDFromBytes("adapt-unarmed-meditation".getBytes(StandardCharsets.UTF_8)).toString());
    when(target.hasAttribute(Attributes.MAX_ABSORPTION)).thenReturn(true);
    when(target.modifiers(Attributes.MAX_ABSORPTION))
        .thenReturn(List.of(new AttributeMod(legacy, 4D, AttributeOperation.ADD_VALUE)));

    UnarmedMeditation.ensureAbsorptionCapacity(target, 6D);

    verify(target).removeModifier(Attributes.MAX_ABSORPTION, legacy);
    verify(target).setTransientModifier(eq(Attributes.MAX_ABSORPTION), any(AttributeMod.class));
  }

  @Test
  void rejectsInvalidCapacityWithoutChangingAttributes() {
    LivingRef target = mock(LivingRef.class);

    assertThat(UnarmedMeditation.ensureAbsorptionCapacity(target, 0D)).isFalse();
    assertThat(UnarmedMeditation.ensureAbsorptionCapacity(target, Double.NaN)).isFalse();

    verifyNoInteractions(target);
  }

  @Test
  void withoutTheCapAttributeCleanupKeepsOnlyWhatAnAbsorptionEffectAllows() {
    LivingRef bare = mock(LivingRef.class);
    when(bare.absorption()).thenReturn(6D);
    LivingRef potion = mock(LivingRef.class);
    when(potion.absorption()).thenReturn(10D);
    when(potion.effect(Id.minecraft("absorption"))).thenReturn(EffectInstance.of(Id.minecraft("absorption"), 400, 0));
    LivingRef capped = mock(LivingRef.class);
    when(capped.hasAttribute(Attributes.MAX_ABSORPTION)).thenReturn(true);
    when(capped.absorption()).thenReturn(6D);

    UnarmedMeditation.removeAbsorptionCapacity(bare);
    UnarmedMeditation.removeAbsorptionCapacity(potion);
    UnarmedMeditation.removeAbsorptionCapacity(capped);

    verify(bare).setAbsorption(0D);
    verify(potion).setAbsorption(4D);
    verify(capped, never()).setAbsorption(anyDouble());
  }

  @Test
  void cleanupRemovesOnlyMeditationsOwnedModifier() {
    LivingRef target = mock(LivingRef.class);
    ArgumentCaptor<Id> modifierKey = ArgumentCaptor.forClass(Id.class);

    UnarmedMeditation.removeAbsorptionCapacity(target);

    verify(target).removeModifier(eq(Attributes.MAX_ABSORPTION), modifierKey.capture());
    assertThat(modifierKey.getValue().toString()).isEqualTo("adapt:unarmed-meditation");
    verify(target, never()).setAttributeBase(any(Id.class), anyDouble());
  }
}
