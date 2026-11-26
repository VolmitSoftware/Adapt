package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TamingHealthBoostTest extends AdaptTestBase {
  @ParameterizedTest
  @CsvSource({"true,40", "true,10", "false,40", "false,10"})
  void removingOwnedHealthBonusClampsOnlyExcessCurrentHealth(boolean cleanup, double health) {
    TameAttributeBoost boost = new TameAttributeBoost(Id.of("adapt", "tame-health-boost"), Attributes.MAX_HEALTH,
        (LivingRef applied) -> {
        });
    LivingRef pet = mock(LivingRef.class);
    when(pet.uuid()).thenReturn(UUID.randomUUID());
    when(pet.isValid()).thenReturn(true);
    when(pet.health()).thenReturn(health);
    when(pet.hasAttribute(Attributes.MAX_HEALTH)).thenReturn(true);
    when(pet.modifier(eq(Attributes.MAX_HEALTH), any()))
        .thenReturn(new AttributeMod(Id.of("adapt", "tame-health-boost"), 1D, AttributeOperation.ADD_MULTIPLIED_BASE));
    when(pet.attributeValue(Attributes.MAX_HEALTH)).thenReturn(20D);

    if (cleanup) {
      boost.remove(pet);
    } else {
      boost.update(pet, 0, 0D);
    }

    verify(pet).removeModifier(eq(Attributes.MAX_HEALTH), any());
    if (health > 20D) {
      verify(pet).setHealth(20D);
    } else {
      verify(pet, never()).setHealth(anyDouble());
    }
  }

  @Test
  void firstActiveSampleDoesNotPreCreditTime() {
    assertThat(TamingHealthBoost.elapsedActiveTicks(null, 4753L)).isZero();
  }

  @Test
  void laterActiveSamplesUseActualElapsedTime() {
    assertThat(TamingHealthBoost.elapsedActiveTicks(1000L, 5753L)).isCloseTo(95.06D, offset(0.0001D));
  }
}
