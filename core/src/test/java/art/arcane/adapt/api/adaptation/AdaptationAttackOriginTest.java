package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AdaptationAttackOriginTest {
  @ParameterizedTest
  @EnumSource(value = DamageCause.class, names = {"MAGIC", "THORNS", "ENTITY_EXPLOSION", "PROJECTILE"})
  void secondaryDamageCannotUseTheCastersCurrentMeleeEquipment(DamageCause cause) {
    Adaptation<?> adaptation = mock(Adaptation.class);
    EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(mock(LivingRef.class), mock(PlayerRef.class), null,
        cause, Id.minecraft("magic"), 4D, false, null);

    assertThat(AdaptationRuntimeGuards.resolveAttackContext(adaptation, event, null)).isNull();
    verifyNoInteractions(adaptation);
  }
}
