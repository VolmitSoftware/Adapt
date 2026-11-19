package art.arcane.adapt.gameplay;

import art.arcane.adapt.platform.GameVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FixturesItemStackNbtTest {
    @Test
    void componentVersionsUseAnIntCount() {
        assertThat(Fixtures.itemStackNbt(new GameVersion(26, 3, 0), "paper", 1)).isEqualTo("{id:\"minecraft:paper\",count:1}");
        assertThat(Fixtures.itemStackNbt(GameVersion.V1_21_1, "emerald", 20)).isEqualTo("{id:\"minecraft:emerald\",count:20}");
    }

    @Test
    void olderVersionsUseACountByte() {
        assertThat(Fixtures.itemStackNbt(GameVersion.V1_20_1, "emerald", 20)).isEqualTo("{id:\"minecraft:emerald\",Count:20b}");
    }
}
