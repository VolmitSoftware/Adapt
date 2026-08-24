package art.arcane.adapt.api.xp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XPMultiplierTest {

    @Test
    @DisplayName("explicit constructor stores the multiplier")
    void explicitConstructorStoresMultiplier() {
        XPMultiplier m = new XPMultiplier(0.25, 60000L);
        assertThat(m.getMultiplier()).isEqualTo(0.25);
    }

    @Test
    @DisplayName("a future expiry is not yet expired")
    void futureExpiryNotExpired() {
        assertThat(new XPMultiplier(0.5, 60000L).isExpired()).isFalse();
    }

    @Test
    @DisplayName("a past expiry is already expired")
    void pastExpiryIsExpired() {
        assertThat(new XPMultiplier(0.5, -1000L).isExpired()).isTrue();
    }

    @Test
    @DisplayName("no-arg constructor yields an unexpired default window")
    void noArgConstructorUnexpired() {
        assertThat(new XPMultiplier().isExpired()).isFalse();
    }

    @Test
    @DisplayName("equal field values are equal")
    void equalityByValue() {
        XPMultiplier a = new XPMultiplier(0.5, 60000L);
        XPMultiplier b = new XPMultiplier(0.5, 60000L);
        b.setGoodFor(a.getGoodFor());
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void durationArithmeticSaturatesInsteadOfWrapping() {
        assertThat(XPMultiplier.expirationTime(Long.MAX_VALUE - 5L, 10L)).isEqualTo(Long.MAX_VALUE);
        assertThat(XPMultiplier.expirationTime(Long.MIN_VALUE + 5L, -10L)).isEqualTo(Long.MIN_VALUE);
        assertThat(XPMultiplier.expirationTime(100L, 20L)).isEqualTo(120L);
    }

    @Test
    void nonFiniteMultipliersAreNeverActive() {
        XPMultiplier multiplier = new XPMultiplier(Double.NaN, 60000L);

        assertThat(multiplier.isActive()).isFalse();
    }
}
