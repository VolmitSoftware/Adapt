package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NetherStriderBondLavaTest {
    @Test
    void detectsLavaBeforeTheRiderFallsFromNaturalDismountHeight() {
        assertThat(NetherStriderBond.isLavaBelowDismount(Id.minecraft("air"), Id.minecraft("air"), Id.minecraft("lava"))).isTrue();
        assertThat(NetherStriderBond.isLavaBelowDismount(Id.minecraft("air"), Id.minecraft("lava"), Id.minecraft("obsidian"))).isTrue();
        assertThat(NetherStriderBond.isLavaBelowDismount(Id.minecraft("lava"), Id.minecraft("obsidian"), Id.minecraft("obsidian"))).isTrue();
    }

    @Test
    void groundBetweenTheRiderAndLavaPreventsARescue() {
        assertThat(NetherStriderBond.isLavaBelowDismount(Id.minecraft("air"), Id.minecraft("obsidian"), Id.minecraft("lava"))).isFalse();
        assertThat(NetherStriderBond.isLavaBelowDismount(Id.minecraft("stone"), Id.minecraft("lava"), Id.minecraft("lava"))).isFalse();
        assertThat(NetherStriderBond.isLavaBelowDismount(Id.minecraft("air"), Id.minecraft("air"), Id.minecraft("obsidian"))).isFalse();
    }
}
