package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.platform.entity.EquipmentSlot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BukkitConversionsVersionTest {
  @Test
  void everyEquipmentSlotRoundTrips() {
    for (EquipmentSlot slot : EquipmentSlot.values()) {
      assertThat(BukkitConversions.slot(BukkitConversions.slot(slot))).isEqualTo(slot);
    }
  }
}
