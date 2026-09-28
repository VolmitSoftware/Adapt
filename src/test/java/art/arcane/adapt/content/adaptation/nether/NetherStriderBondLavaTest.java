package art.arcane.adapt.content.adaptation.nether;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NetherStriderBondLavaTest {
    @Test
    void detectsLavaBeforeTheRiderFallsFromNaturalDismountHeight() {
        assertThat(NetherStriderBond.isLavaBelowDismount(Material.AIR, Material.AIR, Material.LAVA)).isTrue();
        assertThat(NetherStriderBond.isLavaBelowDismount(Material.AIR, Material.LAVA, Material.OBSIDIAN)).isTrue();
        assertThat(NetherStriderBond.isLavaBelowDismount(Material.LAVA, Material.OBSIDIAN, Material.OBSIDIAN)).isTrue();
    }

    @Test
    void groundBetweenTheRiderAndLavaPreventsARescue() {
        assertThat(NetherStriderBond.isLavaBelowDismount(Material.AIR, Material.OBSIDIAN, Material.LAVA)).isFalse();
        assertThat(NetherStriderBond.isLavaBelowDismount(Material.STONE, Material.LAVA, Material.LAVA)).isFalse();
        assertThat(NetherStriderBond.isLavaBelowDismount(Material.AIR, Material.AIR, Material.OBSIDIAN)).isFalse();
    }
}
