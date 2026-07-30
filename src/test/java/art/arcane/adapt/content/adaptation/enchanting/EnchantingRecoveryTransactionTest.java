package art.arcane.adapt.content.adaptation.enchanting;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EnchantingRecoveryTransactionTest {
  private static final Path SOUL_LINK_SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/enchanting/EnchantingSoulLink.java");
  private static final Path OFFER_REROLL_SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/enchanting/EnchantingOfferReroll.java");

  @Test
  void soulLinkUsesDeathSafeRuntimeGatesAndClearsOnlyAfterDelivery() throws Exception {
    String source = Files.readString(SOUL_LINK_SOURCE);
    int restoreStart = source.indexOf("private void restorePending");
    int returnStart = source.indexOf("private void returnSaved", restoreStart);
    String restore = source.substring(restoreStart, returnStart);
    int delivery = source.indexOf("p.getInventory().addItem(saved)", returnStart);
    int clear = source.indexOf("setStorage(p, PENDING_STORAGE_KEY, null)", delivery);
    int rollback = source.indexOf("rollbackDelivery(p, originalContents, droppedItems)", clear);

    assertThat(source).contains("int level = getActiveDeathLevel(p);");
    assertThat(restore).doesNotContain("setStorage(p, PENDING_STORAGE_KEY, null)");
    assertThat(restore).contains("logPendingFailure(p, \"decode\", t)");
    assertThat(delivery).isGreaterThanOrEqualTo(0);
    assertThat(clear).isGreaterThan(delivery);
    assertThat(rollback).isGreaterThan(clear);
    assertThat(source).contains(
        "pendingRestores.remove(e.getPlayer().getUniqueId());",
        "pendingRestores.clear();",
        "p.getInventory().setContents(originalContents);"
    );
  }

  @Test
  void offerRerollDefersAndRollsBackCostAroundSeedMutation() throws Exception {
    String source = Files.readString(OFFER_REROLL_SOURCE);
    int charge = source.indexOf("RerollReservation reservation = reserveRerollCost(p, lapisCost)");
    int seedChange = source.indexOf("if (!setSeed(p, ThreadLocalRandom.current().nextInt()))");
    int settlement = source.indexOf("if (!settleRerollCost(reservation))", seedChange);
    int reservationStart = source.indexOf("private RerollReservation reserveRerollCost");
    int takeLapisStart = source.indexOf("private boolean takeLapis", reservationStart);
    String reservationMethod = source.substring(reservationStart, takeLapisStart);

    assertThat(charge).isGreaterThanOrEqualTo(0);
    assertThat(seedChange).isGreaterThan(charge);
    assertThat(settlement).isGreaterThan(seedChange);
    assertThat(reservationMethod)
        .contains("payItemCostDeferred(")
        .doesNotContain("setSeed(");
    assertThat(source).contains(
        "refundCost(reservation.charge().activationId(), reason)",
        "refundDefaultLapis(p, reservation.lapisAmount())",
        "p.setLevel(reservation.previousXpLevel())"
    );
  }

  @Test
  void offerRerollAcceptsEitherASettledProviderOrAConsumedDefaultCost() {
    assertThat(EnchantingOfferReroll.acceptsDeferredSettlement(true, false)).isTrue();
    assertThat(EnchantingOfferReroll.acceptsDeferredSettlement(false, true)).isTrue();
    assertThat(EnchantingOfferReroll.acceptsDeferredSettlement(false, false)).isFalse();
  }
}
