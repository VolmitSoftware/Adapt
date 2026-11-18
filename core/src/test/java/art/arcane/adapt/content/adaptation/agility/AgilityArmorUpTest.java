package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.platform.value.Id;
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
    Id key = AgilityArmorUp.LEGACY_MODIFIER_KEY;
    assertThat(key).isNotNull();
    assertThat(key.namespace()).isEqualTo("adapt");
    assertThat(key.path()).isEqualTo("armor-up");
  }
}
