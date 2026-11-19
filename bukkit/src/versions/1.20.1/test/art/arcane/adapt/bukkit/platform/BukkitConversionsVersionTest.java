package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.platform.entity.EquipmentSlot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Paper 1.20.1 has neither the body nor the saddle equipment slot. */
class BukkitConversionsVersionTest {
  @Test
  void everyEquipmentSlotButBodyAndSaddleRoundTrips() {
    for (EquipmentSlot slot : EquipmentSlot.values()) {
      if (slot != EquipmentSlot.BODY && slot != EquipmentSlot.SADDLE) {
        assertThat(BukkitConversions.slot(BukkitConversions.slot(slot))).isEqualTo(slot);
      }
    }
    assertThatThrownBy(() -> BukkitConversions.slot(EquipmentSlot.BODY)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> BukkitConversions.slot(EquipmentSlot.SADDLE)).isInstanceOf(IllegalArgumentException.class);
  }
}
