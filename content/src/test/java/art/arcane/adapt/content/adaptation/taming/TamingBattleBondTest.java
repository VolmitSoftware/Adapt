package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.engine.platform.EngineLastDamage;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LastDamage;
import art.arcane.adapt.platform.event.entity.DamageCause;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TamingBattleBondTest {
  @Test
  void buffDurationGrowsWithLevel() {
    assertThat(TamingBattleBond.buffTicks(0.0, 80, 120)).isEqualTo(80);
    assertThat(TamingBattleBond.buffTicks(1.0, 80, 120)).isEqualTo(200);
  }

  @Test
  void buffDurationNeverDropsBelowTheFloor() {
    assertThat(TamingBattleBond.buffTicks(0.0, 5, 0)).isEqualTo(20);
  }

  @Test
  void directCompanionDamageResolvesTheKillingPetWithoutReadingItsOwner() {
    EntityRef pet = mock(EntityRef.class);
    when(pet.has(EntityTrait.TAMEABLE)).thenReturn(true);
    LastDamage cause = new EngineLastDamage(DamageCause.ENTITY_ATTACK, pet, 4D);

    assertThat(TamingBattleBond.resolveKillingPet(cause)).isSameAs(pet);
  }

  @Test
  void battleBondIncludesSpeedRegenerationAndAttackStrength() {
    assertThat(TamingBattleBond.buffTypes(true)).containsExactly(
        TamingBattleBond.BondBuff.SPEED,
        TamingBattleBond.BondBuff.REGENERATION,
        TamingBattleBond.BondBuff.ATTACK_STRENGTH);
    assertThat(TamingBattleBond.buffTypes(false)).containsExactly(
        TamingBattleBond.BondBuff.SPEED,
        TamingBattleBond.BondBuff.REGENERATION);
  }
}
