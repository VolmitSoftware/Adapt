package art.arcane.adapt.content.adaptation.taming;

import org.bukkit.entity.Tameable;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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
    EntityDamageByEntityEvent cause = mock(EntityDamageByEntityEvent.class);
    Tameable pet = mock(Tameable.class);
    when(cause.getDamager()).thenReturn(pet);

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
