package art.arcane.adapt.api.adaptation;

import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdaptationAttackOriginTest {
  @ParameterizedTest
  @EnumSource(value = EntityDamageEvent.DamageCause.class, names = {"MAGIC", "THORNS", "ENTITY_EXPLOSION", "PROJECTILE"})
  void secondaryDamageCannotUseTheCastersCurrentMeleeEquipment(EntityDamageEvent.DamageCause cause) {
    Adaptation<?> adaptation = mock(Adaptation.class);
    EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
    when(event.getCause()).thenReturn(cause);

    assertThat(AdaptationRuntimeGuards.resolveAttackContext(adaptation, event, null)).isNull();
    verifyNoInteractions(adaptation);
  }
}
