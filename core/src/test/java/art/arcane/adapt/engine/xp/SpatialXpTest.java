package art.arcane.adapt.engine.xp;

import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SpatialXpTest {

    @Test
    @DisplayName("constructor exposes location, xp and radius")
    void constructorExposesFields() {
        Loc loc = new Loc(mock(WorldRef.class), 1D, 64D, 2D);
        SpatialXP s = new SpatialXP(loc, null, 5.0, 3.0, 1000L);
        assertThat(s.getXp()).isEqualTo(5.0);
        assertThat(s.getRadius()).isEqualTo(3.0);
        assertThat(s.getLocation()).isSameAs(loc);
    }

    @Test
    @DisplayName("expiry timestamp is in the future for a positive duration")
    void expiryInFutureForPositiveDuration() {
        Loc loc = new Loc(mock(WorldRef.class), 1D, 64D, 2D);
        SpatialXP s = new SpatialXP(loc, null, 1.0, 1.0, 5000L);
        assertThat(s.getMs()).isGreaterThan(0L);
    }

    @Test
    @DisplayName("setters mutate the orb state")
    void settersMutate() {
        Loc loc = new Loc(mock(WorldRef.class), 1D, 64D, 2D);
        SpatialXP s = new SpatialXP(loc, null, 1.0, 1.0, 1000L);
        s.setXp(9.0);
        s.setRadius(7.0);
        assertThat(s.getXp()).isEqualTo(9.0);
        assertThat(s.getRadius()).isEqualTo(7.0);
    }
}
