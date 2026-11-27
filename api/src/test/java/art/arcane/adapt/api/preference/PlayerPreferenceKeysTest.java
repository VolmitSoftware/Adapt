package art.arcane.adapt.api.preference;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The conventional label keys never make one key both a message and a table, so TOML can hold them. */
class PlayerPreferenceKeysTest {
  private enum Delivery {
    FAST,
    GENTLE
  }

  private static final PlayerPreference<Delivery> DELIVERY =
      PlayerPreference.of("delivery", Delivery.class, Delivery.FAST);

  @Test
  void theLabelSitsBesideTheChoicesInsteadOfAboveThem() {
    assertThat(DELIVERY.conventionalKey()).isEqualTo("preference.delivery.name");
    assertThat(DELIVERY.conventionalKey(Delivery.GENTLE)).isEqualTo("preference.delivery.choice.gentle");
  }

  @Test
  void noConventionalKeyIsAPrefixOfAnother() {
    String label = DELIVERY.conventionalKey() + ".";
    for (Delivery value : Delivery.values()) {
      String choice = DELIVERY.conventionalKey(value);
      assertThat(choice).doesNotStartWith(label);
      assertThat(DELIVERY.conventionalKey()).doesNotStartWith(choice + ".");
    }
  }
}
