package art.arcane.adapt.content.adaptation.architect;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectScaffolderFilterTest {
  @Test
  void filterAndHungerCostAreOffByDefault() {
    ArchitectScaffolder.Config config = new ArchitectScaffolder.Config();

    assertThat(config.blockFilterMode).isEqualTo("OFF");
    assertThat(config.blockFilterMaterials).isEmpty();
    assertThat(config.hungerExhaustionPerScaffold).isZero();
    assertThat(ArchitectScaffolder.isScaffoldAllowed(config.blockFilterMode,
        ArchitectScaffolder.parseFilterMaterials(config.blockFilterMaterials), Material.TNT)).isTrue();
  }

  @Test
  void blacklistDeniesOnlyListedMaterials() {
    Set<Material> denied = ArchitectScaffolder.parseFilterMaterials(List.of("TNT", "sand"));

    assertThat(ArchitectScaffolder.isScaffoldAllowed("BLACKLIST", denied, Material.TNT)).isFalse();
    assertThat(ArchitectScaffolder.isScaffoldAllowed("BLACKLIST", denied, Material.SAND)).isFalse();
    assertThat(ArchitectScaffolder.isScaffoldAllowed("BLACKLIST", denied, Material.STONE)).isTrue();
  }

  @Test
  void whitelistAllowsOnlyListedMaterials() {
    Set<Material> allowed = ArchitectScaffolder.parseFilterMaterials(List.of("stone"));

    assertThat(ArchitectScaffolder.isScaffoldAllowed("WHITELIST", allowed, Material.STONE)).isTrue();
    assertThat(ArchitectScaffolder.isScaffoldAllowed("WHITELIST", allowed, Material.TNT)).isFalse();
    assertThat(ArchitectScaffolder.isScaffoldAllowed("whitelist", allowed, Material.STONE)).isTrue();
  }

  @Test
  void unknownOrMissingModesApplyNoFilter() {
    Set<Material> denied = ArchitectScaffolder.parseFilterMaterials(List.of("STONE"));

    assertThat(ArchitectScaffolder.isScaffoldAllowed("OFF", denied, Material.STONE)).isTrue();
    assertThat(ArchitectScaffolder.isScaffoldAllowed("nonsense", denied, Material.STONE)).isTrue();
    assertThat(ArchitectScaffolder.isScaffoldAllowed(null, denied, Material.STONE)).isTrue();
    assertThat(ArchitectScaffolder.isScaffoldAllowed("BLACKLIST", denied, null)).isTrue();
  }

  @Test
  void filterParsingIgnoresBlankAndUnknownMaterialNames() {
    Set<Material> materials = ArchitectScaffolder.parseFilterMaterials(
        Arrays.asList("STONE", " ", null, "NOT_A_REAL_BLOCK", " tnt "));

    assertThat(materials).containsExactlyInAnyOrder(Material.STONE, Material.TNT);
    assertThat(ArchitectScaffolder.parseFilterMaterials(null)).isEmpty();
  }
}
