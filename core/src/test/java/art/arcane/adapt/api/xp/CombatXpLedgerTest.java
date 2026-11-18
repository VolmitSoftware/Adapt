package art.arcane.adapt.api.xp;

import art.arcane.adapt.util.common.io.Json;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CombatXpLedgerTest {
  @Test
  void overkillAndPreventedDamageDoNotCount() {
    assertThat(CombatXp.effectiveDamage(100D, 2D)).isEqualTo(2D);
    assertThat(CombatXp.effectiveDamage(0D, 20D)).isZero();
    assertThat(CombatXp.effectiveDamage(Double.NaN, 20D)).isZero();
    assertThat(CombatXp.effectiveDamage(5D, -1D)).isZero();
  }

  @Test
  void healingTheSameVictimCannotRefreshItsBudget() {
    CombatXpLedger ledger = new CombatXpLedger();
    UUID victim = UUID.randomUUID();
    assertThat(ledger.claim("swords", victim, 15D, 20D, 1000L)).isEqualTo(15D);
    assertThat(ledger.claim("swords", victim, 15D, 20D, 2000L)).isEqualTo(5D);
    assertThat(ledger.claim("swords", victim, 15D, 200D, 3000L)).isZero();
    assertThat(ledger.claim("swords", victim, 15D, 20D, 601000L)).isEqualTo(15D);
  }

  @Test
  void victimsAndSkillsHaveIndependentBudgets() {
    CombatXpLedger ledger = new CombatXpLedger();
    UUID victim = UUID.randomUUID();
    assertThat(ledger.claim("swords", victim, 20D, 20D, 1000L)).isEqualTo(20D);
    assertThat(ledger.claim("stealth", victim, 20D, 20D, 1000L)).isEqualTo(20D);
    assertThat(ledger.claim("swords", UUID.randomUUID(), 20D, 20D, 1000L)).isEqualTo(20D);
  }

  @Test
  void profileRoundTripRetainsExhaustedVictims() {
    CombatXpLedger ledger = new CombatXpLedger();
    UUID victim = UUID.randomUUID();
    ledger.claim("swords", victim, 20D, 20D, 1000L);
    CombatXpLedger restored = Json.fromJson(Json.toJson(ledger, false), CombatXpLedger.class);
    assertThat(restored.claim("swords", victim, 20D, 20D, 2000L)).isZero();
    assertThat(restored.claim("swords", victim, 20D, 20D, 500L)).isZero();
  }
}
