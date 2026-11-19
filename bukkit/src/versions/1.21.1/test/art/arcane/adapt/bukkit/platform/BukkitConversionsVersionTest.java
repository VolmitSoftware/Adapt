package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.platform.entity.EquipmentSlot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Paper 1.21.1 has no saddle equipment slot. */
class BukkitConversionsVersionTest {
  @Test
  void everyEquipmentSlotButTheSaddleRoundTrips() {
    for (EquipmentSlot slot : EquipmentSlot.values()) {
      if (slot != EquipmentSlot.SADDLE) {
        assertThat(BukkitConversions.slot(BukkitConversions.slot(slot))).isEqualTo(slot);
      }
    }
    assertThatThrownBy(() -> BukkitConversions.slot(EquipmentSlot.SADDLE)).isInstanceOf(IllegalArgumentException.class);
  }
}
