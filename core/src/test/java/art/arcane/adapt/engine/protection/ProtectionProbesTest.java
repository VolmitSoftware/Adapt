package art.arcane.adapt.engine.protection;

import art.arcane.adapt.testkit.FakeInventory;
import art.arcane.adapt.testkit.FakeItem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProtectionProbesTest {
  @Test
  void remainingAfterPickupCountsFreeSlotsAndPartialStacks() {
    FakeInventory inventory = new FakeInventory();
    for (int slot = 0; slot < 36; slot++) {
      inventory.setItem(slot, FakeItem.of("stone").copyWithAmount(64));
    }
    inventory.setItem(4, FakeItem.of("raw_iron").copyWithAmount(60));

    assertThat(ProtectionProbes.remainingAfterPickup(inventory, FakeItem.of("raw_iron").copyWithAmount(10))).isEqualTo(6);
    assertThat(ProtectionProbes.remainingAfterPickup(inventory, FakeItem.of("raw_iron").copyWithAmount(4))).isZero();

    inventory.clear(7);
    assertThat(ProtectionProbes.remainingAfterPickup(inventory, FakeItem.of("raw_iron").copyWithAmount(10))).isZero();
  }

  @Test
  void armourAndOffHandSlotsNeverTakePickups() {
    FakeInventory inventory = new FakeInventory();
    for (int slot = 0; slot < 36; slot++) {
      inventory.setItem(slot, FakeItem.of("stone").copyWithAmount(64));
    }

    assertThat(ProtectionProbes.remainingAfterPickup(inventory, FakeItem.of("coal").copyWithAmount(3))).isEqualTo(3);
  }
}
