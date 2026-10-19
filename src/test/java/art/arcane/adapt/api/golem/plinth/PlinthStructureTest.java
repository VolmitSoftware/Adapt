package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.api.golem.GolemRoster;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlinthStructureTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final Set<Material> PEDESTALS = PlinthStructure.pedestals(PlinthStructure.DEFAULT_PEDESTALS, material -> true, message -> {
  });

  private final Map<String, Material> blocks = new HashMap<>();
  private final PlinthStructure.Blocks lookup = (x, y, z) -> blocks.getOrDefault(x + "," + y + "," + z, Material.AIR);

  @Test
  void theDefaultPedestalsAreTheEightChiseledCopperBlocks() {
    assertThat(PEDESTALS).containsExactlyInAnyOrder(
        Material.CHISELED_COPPER, Material.EXPOSED_CHISELED_COPPER, Material.WEATHERED_CHISELED_COPPER, Material.OXIDIZED_CHISELED_COPPER,
        Material.WAXED_CHISELED_COPPER, Material.WAXED_EXPOSED_CHISELED_COPPER, Material.WAXED_WEATHERED_CHISELED_COPPER,
        Material.WAXED_OXIDIZED_CHISELED_COPPER);
  }

  @Test
  void unknownPedestalNamesAndTheLodestoneAreDroppedWithAWarning() {
    List<String> warnings = new ArrayList<>();

    Set<Material> pedestals = PlinthStructure.pedestals(List.of("chiseled_copper", "NOT_A_BLOCK", "LODESTONE", " WAXED_CHISELED_COPPER "), material -> true, warnings::add);

    assertThat(pedestals).containsExactlyInAnyOrder(Material.CHISELED_COPPER, Material.WAXED_CHISELED_COPPER);
    assertThat(warnings).hasSize(2);
    assertThat(warnings.get(0)).contains("NOT_A_BLOCK");
    assertThat(warnings.get(1)).contains("LODESTONE");
  }

  @Test
  void namesThatAreNotBlocksAreDroppedWithAWarningEach() {
    List<String> warnings = new ArrayList<>();

    Set<Material> pedestals = PlinthStructure.pedestals(List.of("CHISELED_COPPER", "DIAMOND", "STICK"),
        material -> material == Material.CHISELED_COPPER, warnings::add);

    assertThat(pedestals).containsExactly(Material.CHISELED_COPPER);
    assertThat(warnings).hasSize(2);
    assertThat(warnings.get(0)).contains("DIAMOND");
    assertThat(warnings.get(1)).contains("STICK");
  }

  @Test
  void aLodestoneWithFourPedestalsTwoBlocksAwayIsValid() {
    build(0, 64, 0, Material.CHISELED_COPPER);

    assertThat(PlinthStructure.valid(lookup, spot(0, 64, 0), PEDESTALS)).isTrue();
  }

  @Test
  void mixedPedestalVariantsStillFormAPlinth() {
    build(10, 70, -5, Material.OXIDIZED_CHISELED_COPPER);
    blocks.put("12,70,-5", Material.WAXED_EXPOSED_CHISELED_COPPER);

    assertThat(PlinthStructure.valid(lookup, spot(10, 70, -5), PEDESTALS)).isTrue();
  }

  @Test
  void everyMissingPedestalBreaksTheStructure() {
    for (PlinthSlot slot : PlinthSlot.PEDESTALS) {
      blocks.clear();
      build(0, 64, 0, Material.CHISELED_COPPER);
      blocks.put(slot.dx() + ",64," + slot.dz(), Material.COPPER_BLOCK);

      assertThat(PlinthStructure.valid(lookup, spot(0, 64, 0), PEDESTALS)).as("missing %s", slot).isFalse();
    }
  }

  @Test
  void theCenterMustBeALodestone() {
    build(0, 64, 0, Material.CHISELED_COPPER);
    blocks.put("0,64,0", Material.CHISELED_COPPER);

    assertThat(PlinthStructure.valid(lookup, spot(0, 64, 0), PEDESTALS)).isFalse();
  }

  @Test
  void pedestalsAtTheWrongDistanceOrHeightDoNotCount() {
    blocks.put("0,64,0", Material.LODESTONE);
    blocks.put("0,64,-1", Material.CHISELED_COPPER);
    blocks.put("1,64,0", Material.CHISELED_COPPER);
    blocks.put("0,64,1", Material.CHISELED_COPPER);
    blocks.put("-1,64,0", Material.CHISELED_COPPER);
    assertThat(PlinthStructure.valid(lookup, spot(0, 64, 0), PEDESTALS)).isFalse();

    blocks.clear();
    blocks.put("0,64,0", Material.LODESTONE);
    blocks.put("0,64,-3", Material.CHISELED_COPPER);
    blocks.put("3,64,0", Material.CHISELED_COPPER);
    blocks.put("0,64,3", Material.CHISELED_COPPER);
    blocks.put("-3,64,0", Material.CHISELED_COPPER);
    assertThat(PlinthStructure.valid(lookup, spot(0, 64, 0), PEDESTALS)).isFalse();

    blocks.clear();
    blocks.put("0,64,0", Material.LODESTONE);
    blocks.put("0,65,-2", Material.CHISELED_COPPER);
    blocks.put("2,65,0", Material.CHISELED_COPPER);
    blocks.put("0,65,2", Material.CHISELED_COPPER);
    blocks.put("-2,65,0", Material.CHISELED_COPPER);
    assertThat(PlinthStructure.valid(lookup, spot(0, 64, 0), PEDESTALS)).isFalse();
  }

  @Test
  void blocksInUnloadedChunksNeverFormAPlinth() {
    build(0, 64, 0, Material.CHISELED_COPPER);
    PlinthStructure.Blocks partial = (x, y, z) -> x > 1 ? null : lookup.type(x, y, z);

    assertThat(PlinthStructure.valid(partial, spot(0, 64, 0), PEDESTALS)).isFalse();
    assertThat(PlinthStructure.locate(partial, spot(0, 64, 0), PEDESTALS)).isEmpty();
  }

  @Test
  void aConfiguredPedestalListReplacesTheDefault() {
    build(0, 64, 0, Material.CHISELED_COPPER);
    Set<Material> polished = PlinthStructure.pedestals(List.of("POLISHED_ANDESITE"), material -> true, message -> {
    });

    assertThat(PlinthStructure.valid(lookup, spot(0, 64, 0), polished)).isFalse();
    build(0, 64, 0, Material.POLISHED_ANDESITE);
    assertThat(PlinthStructure.valid(lookup, spot(0, 64, 0), polished)).isTrue();
  }

  @Test
  void locatingFromTheCenterOrAnyPedestalFindsTheSamePlinth() {
    build(5, 64, 5, Material.CHISELED_COPPER);
    PlinthSpot center = spot(5, 64, 5);

    assertThat(PlinthStructure.locate(lookup, center, PEDESTALS)).contains(new PlinthStructure.PlinthTarget(center, PlinthSlot.CENTER));
    for (PlinthSlot slot : PlinthSlot.PEDESTALS) {
      Optional<PlinthStructure.PlinthTarget> target = PlinthStructure.locate(lookup, center.slot(slot), PEDESTALS);

      assertThat(target).as("from %s", slot).contains(new PlinthStructure.PlinthTarget(center, slot));
      assertThat(target.get().spot()).isEqualTo(center.slot(slot));
    }
  }

  @Test
  void blocksOutsideAValidPlinthLocateNothing() {
    build(5, 64, 5, Material.CHISELED_COPPER);
    blocks.put("9,64,9", Material.CHISELED_COPPER);
    blocks.put("20,64,20", Material.LODESTONE);

    assertThat(PlinthStructure.locate(lookup, spot(9, 64, 9), PEDESTALS)).isEmpty();
    assertThat(PlinthStructure.locate(lookup, spot(20, 64, 20), PEDESTALS)).isEmpty();
    assertThat(PlinthStructure.locate(lookup, spot(6, 64, 5), PEDESTALS)).isEmpty();
  }

  @Test
  void aSharedPedestalBelongsToTheFirstValidPlinth() {
    build(0, 64, 0, Material.CHISELED_COPPER);
    build(4, 64, 0, Material.CHISELED_COPPER);

    assertThat(PlinthStructure.locate(lookup, spot(2, 64, 0), PEDESTALS))
        .contains(new PlinthStructure.PlinthTarget(spot(0, 64, 0), PlinthSlot.EAST));
  }

  @Test
  void plinthPartsAreLodestonesAndPedestalBlocks() {
    assertThat(PlinthStructure.isPart(Material.LODESTONE, PEDESTALS)).isTrue();
    assertThat(PlinthStructure.isPart(Material.WAXED_CHISELED_COPPER, PEDESTALS)).isTrue();
    assertThat(PlinthStructure.isPart(Material.COPPER_BLOCK, PEDESTALS)).isFalse();
  }

  @Test
  void spotsKnowTheirSlotsAndChunk() {
    PlinthSpot center = spot(-17, 70, 33);

    assertThat(center.slot(PlinthSlot.NORTH)).isEqualTo(spot(-17, 70, 31));
    assertThat(center.slot(PlinthSlot.EAST)).isEqualTo(spot(-15, 70, 33));
    assertThat(center.slot(PlinthSlot.SOUTH)).isEqualTo(spot(-17, 70, 35));
    assertThat(center.slot(PlinthSlot.WEST)).isEqualTo(spot(-19, 70, 33));
    assertThat(center.slot(PlinthSlot.CENTER)).isEqualTo(center);
    assertThat(center.chunkKey()).isEqualTo(GolemRoster.chunkKey(-2, 2));
  }

  private void build(int x, int y, int z, Material pedestal) {
    blocks.put(x + "," + y + "," + z, Material.LODESTONE);
    for (PlinthSlot slot : PlinthSlot.PEDESTALS) {
      blocks.put((x + slot.dx()) + "," + y + "," + (z + slot.dz()), pedestal);
    }
  }

  private static PlinthSpot spot(int x, int y, int z) {
    return new PlinthSpot(WORLD, x, y, z);
  }
}
