package art.arcane.adapt.content.adaptation.stealth;

import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StealthAssassinateDamageTest {
  @Test
  void executionBypassesArmorResistanceAndAbsorptionWithoutTouchingUnsupportedModifiers() {
    EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
    when(event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR)).thenReturn(true);
    when(event.isApplicable(EntityDamageEvent.DamageModifier.RESISTANCE)).thenReturn(true);
    when(event.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION)).thenReturn(true);

    StealthAssassinate.applyExecutionDamage(event, 20D);

    verify(event).setDamage(20D);
    verify(event).setDamage(EntityDamageEvent.DamageModifier.ARMOR, 0D);
    verify(event).setDamage(EntityDamageEvent.DamageModifier.RESISTANCE, 0D);
    verify(event).setDamage(EntityDamageEvent.DamageModifier.ABSORPTION, 0D);
    verify(event, never()).setDamage(eq(EntityDamageEvent.DamageModifier.BLOCKING), anyDouble());
    verify(event, never()).setDamage(eq(EntityDamageEvent.DamageModifier.BASE), anyDouble());
  }
}
