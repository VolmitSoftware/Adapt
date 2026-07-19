package art.arcane.adapt.api.attribute;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptAttributeKeyTest {
  @Test
  void sameInputsProduceIdenticalKeyAndUuid() {
    AdaptAttributeKey first = AdaptAttributeKey.of("agility-wind-up", "boost");
    AdaptAttributeKey second = AdaptAttributeKey.of("agility-wind-up", "boost");

    assertThat(first).isEqualTo(second);
    assertThat(first.key()).isEqualTo(second.key());
    assertThat(first.uuid()).isEqualTo(second.uuid());
  }

  @Test
  void uuidIsDerivedFromKeyString() {
    AdaptAttributeKey key = AdaptAttributeKey.of("stealth-speed", null);

    UUID expected = UUID.nameUUIDFromBytes(key.key().toString().getBytes(StandardCharsets.UTF_8));

    assertThat(key.key().toString()).isEqualTo("adaptbuff:stealth-speed");
    assertThat(key.uuid()).isEqualTo(expected);
  }

  @Test
  void hostileNamesAreSanitized() {
    AdaptAttributeKey key = AdaptAttributeKey.of("Sneaky Ability!?", "Main Hand");

    assertThat(key.key().getNamespace()).isEqualTo("adaptbuff");
    assertThat(key.key().getKey()).isEqualTo("sneaky_ability___main_hand");
  }

  @Test
  void allowedCharactersAreKept() {
    AdaptAttributeKey key = AdaptAttributeKey.of("a-b_c.9", null);

    assertThat(key.key().getKey()).isEqualTo("a-b_c.9");
  }

  @Test
  void emptySlotOmitsSuffix() {
    AdaptAttributeKey noSlot = AdaptAttributeKey.of("hunter-luck", null);
    AdaptAttributeKey emptySlot = AdaptAttributeKey.of("hunter-luck", "");

    assertThat(noSlot.key().getKey()).isEqualTo("hunter-luck");
    assertThat(emptySlot).isEqualTo(noSlot);
  }

  @Test
  void differentSlotsProduceDifferentUuids() {
    AdaptAttributeKey chest = AdaptAttributeKey.of("armor-up", "chest");
    AdaptAttributeKey legs = AdaptAttributeKey.of("armor-up", "legs");

    assertThat(chest.uuid()).isNotEqualTo(legs.uuid());
    assertThat(chest.key()).isNotEqualTo(legs.key());
  }
}
