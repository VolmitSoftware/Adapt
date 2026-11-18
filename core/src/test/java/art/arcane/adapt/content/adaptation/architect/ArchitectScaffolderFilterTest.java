package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectScaffolderFilterTest {
  private static final Set<Id> REGISTRY = Set.of(Id.minecraft("stone"), Id.minecraft("tnt"), Id.minecraft("sand"));
  private static final Predicate<Id> KNOWN = REGISTRY::contains;

  @Test
  void filterAndHungerCostAreOffByDefault() {
    ArchitectScaffolder.Config config = new ArchitectScaffolder.Config();

    assertThat(config.blockFilterMode).isEqualTo("OFF");
    assertThat(config.blockFilterMaterials).isEmpty();
    assertThat(config.hungerExhaustionPerScaffold).isZero();
    assertThat(ArchitectScaffolder.isScaffoldAllowed(config.blockFilterMode,
        ArchitectScaffolder.parseFilterMaterials(config.blockFilterMaterials, KNOWN), Id.minecraft("tnt"))).isTrue();
  }

  @Test
  void blacklistDeniesOnlyListedMaterials() {
    Set<Id> denied = ArchitectScaffolder.parseFilterMaterials(List.of("TNT", "sand"), KNOWN);

    assertThat(ArchitectScaffolder.isScaffoldAllowed("BLACKLIST", denied, Id.minecraft("tnt"))).isFalse();
    assertThat(ArchitectScaffolder.isScaffoldAllowed("BLACKLIST", denied, Id.minecraft("sand"))).isFalse();
    assertThat(ArchitectScaffolder.isScaffoldAllowed("BLACKLIST", denied, Id.minecraft("stone"))).isTrue();
  }

  @Test
  void whitelistAllowsOnlyListedMaterials() {
    Set<Id> allowed = ArchitectScaffolder.parseFilterMaterials(List.of("stone"), KNOWN);

    assertThat(ArchitectScaffolder.isScaffoldAllowed("WHITELIST", allowed, Id.minecraft("stone"))).isTrue();
    assertThat(ArchitectScaffolder.isScaffoldAllowed("WHITELIST", allowed, Id.minecraft("tnt"))).isFalse();
    assertThat(ArchitectScaffolder.isScaffoldAllowed("whitelist", allowed, Id.minecraft("stone"))).isTrue();
  }

  @Test
  void unknownOrMissingModesApplyNoFilter() {
    Set<Id> denied = ArchitectScaffolder.parseFilterMaterials(List.of("STONE"), KNOWN);

    assertThat(ArchitectScaffolder.isScaffoldAllowed("OFF", denied, Id.minecraft("stone"))).isTrue();
    assertThat(ArchitectScaffolder.isScaffoldAllowed("nonsense", denied, Id.minecraft("stone"))).isTrue();
    assertThat(ArchitectScaffolder.isScaffoldAllowed(null, denied, Id.minecraft("stone"))).isTrue();
    assertThat(ArchitectScaffolder.isScaffoldAllowed("BLACKLIST", denied, null)).isTrue();
  }

  @Test
  void filterParsingIgnoresBlankAndUnknownMaterialNames() {
    Set<Id> materials = ArchitectScaffolder.parseFilterMaterials(
        Arrays.asList("STONE", " ", null, "NOT_A_REAL_BLOCK", " tnt "), KNOWN);

    assertThat(materials).containsExactlyInAnyOrder(Id.minecraft("stone"), Id.minecraft("tnt"));
    assertThat(ArchitectScaffolder.parseFilterMaterials(null, KNOWN)).isEmpty();
  }

  @Test
  void filterNamesReadLikeTheBukkitUpperCasedMatchMaterial() {
    Predicate<Id> known = id -> REGISTRY.contains(id) || id.equals(Id.parse("othermod:peat"));

    assertThat(ArchitectScaffolder.parseFilterMaterials(List.of("Stone", "minecraft:tnt", "othermod:peat"), known))
        .containsExactlyInAnyOrder(Id.minecraft("stone"), Id.parse("othermod:peat"));
  }
}
