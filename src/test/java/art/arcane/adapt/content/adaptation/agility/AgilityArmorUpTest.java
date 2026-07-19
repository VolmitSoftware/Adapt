package art.arcane.adapt.content.adaptation.agility;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AgilityArmorUpTest {
  @Test
  void legacyModifierUuidMatchesHistoricalPersistentModifier() {
    assertThat(AgilityArmorUp.LEGACY_MODIFIER).isEqualTo(UUID.fromString("2b50930a-f13e-3033-a2f2-57c8e257fd73"));
    assertThat(AgilityArmorUp.LEGACY_MODIFIER).isEqualTo(UUID.nameUUIDFromBytes("adapt-armor-up".getBytes()));
  }

  @Test
  void legacyModifierKeyTargetsAdaptNamespacedArmorUp() {
    NamespacedKey key = AgilityArmorUp.LEGACY_MODIFIER_KEY;
    assertThat(key).isNotNull();
    assertThat(key.getNamespace()).isEqualTo("adapt");
    assertThat(key.getKey()).isEqualTo("armor-up");
  }
}
