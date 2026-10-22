package art.arcane.adapt.api.xp;

import art.arcane.adapt.util.common.io.Json;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItemTransferXpLedgerTest {
  @Test
  void repeatedStorageCannotMultiplyOneStackAllowance() {
    ItemTransferXpLedger ledger = new ItemTransferXpLedger();
    assertThat(ledger.claim("STONE", 64, 64, 600000L, 1000L)).isEqualTo(64D);
    assertThat(ledger.claim("STONE", 64, 64, 600000L, 1000L)).isZero();
    assertThat(ledger.claim("STONE", 64, 64, 600000L, 301000L)).isEqualTo(32D);
    assertThat(ledger.claim("STONE", 64, 64, 600000L, 301000L)).isZero();
  }

  @Test
  void stackLimitsAndMaterialsRemainIndependent() {
    ItemTransferXpLedger ledger = new ItemTransferXpLedger();
    assertThat(ledger.claim("ENDER_PEARL", 64, 16, 600000L, 1000L)).isEqualTo(16D);
    assertThat(ledger.claim("DIAMOND_SWORD", 64, 1, 600000L, 1000L)).isEqualTo(1D);
    assertThat(ledger.claim("STONE", 64, 64, 600000L, 1000L)).isEqualTo(64D);
  }

  @Test
  void profileRoundTripAndClockRollbackDoNotResetAllowance() {
    ItemTransferXpLedger ledger = new ItemTransferXpLedger();
    ledger.claim("STONE", 64, 64, 600000L, 1000L);
    ItemTransferXpLedger restored = Json.fromJson(Json.toJson(ledger, false), ItemTransferXpLedger.class);
    assertThat(restored.claim("STONE", 64, 64, 600000L, 500L)).isZero();
    assertThat(restored.claim("STONE", 64, 64, 600000L, 1000L)).isZero();
    assertThat(restored.claim("STONE", 128, 64, 600000L, 1201000L)).isEqualTo(64D);
  }
}
