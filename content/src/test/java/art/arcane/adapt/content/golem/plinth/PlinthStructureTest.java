package art.arcane.adapt.content.golem.plinth;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.TestCores;
import art.arcane.adapt.content.golem.GolemRoster;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlinthStructureTest {
  private static final Set<String> KNOWN_BLOCKS = Set.of("chiseled_copper", "waxed_chiseled_copper", "lodestone", "diamond", "stick",
      "polished_andesite", "copper_block");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final Set<Id> PEDESTALS = PlinthStructure.pedestals(PlinthStructure.DEFAULT_PEDESTALS, material -> true, message -> {
  });

  private final Map<String, Id> blocks = new HashMap<>();
  private final PlinthStructure.Blocks lookup = (x, y, z) -> blocks.getOrDefault(x + "," + y + "," + z, Id.minecraft("air"));

  @Test
  void theDefaultPedestalsAreTheEightChiseledCopperBlocks() {
    assertThat(PEDESTALS).containsExactlyInAnyOrder(
        Id.minecraft("chiseled_copper"), Id.minecraft("exposed_chiseled_copper"), Id.minecraft("weathered_chiseled_copper"), Id.minecraft("oxidized_chiseled_copper"),
        Id.minecraft("waxed_chiseled_copper"), Id.minecraft("waxed_exposed_chiseled_copper"), Id.minecraft("waxed_weathered_chiseled_copper"),
        Id.minecraft("waxed_oxidized_chiseled_copper"));
  }

  @Test
  void unknownPedestalNamesAndTheLodestoneAreDroppedWithAWarning() {
    List<String> warnings = new ArrayList<>();

    Set<Id> pedestals = withBlockRegistry(() -> PlinthStructure.pedestals(List.of("chiseled_copper", "NOT_A_BLOCK", "LODESTONE", " WAXED_CHISELED_COPPER "), material -> true, warnings::add));

    assertThat(pedestals).containsExactlyInAnyOrder(Id.minecraft("chiseled_copper"), Id.minecraft("waxed_chiseled_copper"));
    assertThat(warnings).hasSize(2);
    assertThat(warnings.get(0)).contains("NOT_A_BLOCK");
    assertThat(warnings.get(1)).contains("LODESTONE");
  }

  @Test
  void namesThatAreNotBlocksAreDroppedWithAWarningEach() {
    List<String> warnings = new ArrayList<>();

    Set<Id> pedestals = PlinthStructure.pedestals(List.of("CHISELED_COPPER", "DIAMOND", "STICK"),
        material -> material.equals(Id.minecraft("chiseled_copper")), warnings::add);

    assertThat(pedestals).containsExactly(Id.minecraft("chiseled_copper"));
    assertThat(warnings).hasSize(2);
    assertThat(warnings.get(0)).contains("DIAMOND");
    assertThat(warnings.get(1)).contains("STICK");
  }

  @Test
  void aLodestoneWithFourPedestalsTwoBlocksAwayIsValid() {
    build(0, 64, 0, Id.minecraft("chiseled_copper"));

    assertThat(PlinthStructure.valid(lookup, spot(0, 64, 0), PEDESTALS)).isTrue();
  }

  @Test
  void mixedPedestalVariantsStillFormAPlinth() {
    build(10, 70, -5, Id.minecraft("oxidized_chiseled_copper"));
    blocks.put("12,70,-5", Id.minecraft("waxed_exposed_chiseled_copper"));

    assertThat(PlinthStructure.valid(lookup, spot(10, 70, -5), PEDESTALS)).isTrue();
  }

  @Test
  void everyMissingPedestalBreaksTheStructure() {
    for (PlinthSlot slot : PlinthSlot.PEDESTALS) {
      blocks.clear();
      build(0, 64, 0, Id.minecraft("chiseled_copper"));
      blocks.put(slot.dx() + ",64," + slot.dz(), Id.minecraft("copper_block"));

      assertThat(PlinthStructure.valid(lookup, spot(0, 64, 0), PEDESTALS)).as("missing %s", slot).isFalse();
    }
  }

  @Test
  void theCenterMustBeALodestone() {
    build(0, 64, 0, Id.minecraft("chiseled_copper"));
    blocks.put("0,64,0", Id.minecraft("chiseled_copper"));

    assertThat(PlinthStructure.valid(lookup, spot(0, 64, 0), PEDESTALS)).isFalse();
  }

  @Test
  void pedestalsAtTheWrongDistanceOrHeightDoNotCount() {
    blocks.put("0,64,0", Id.minecraft("lodestone"));
    blocks.put("0,64,-1", Id.minecraft("chiseled_copper"));
    blocks.put("1,64,0", Id.minecraft("chiseled_copper"));
    blocks.put("0,64,1", Id.minecraft("chiseled_copper"));
    blocks.put("-1,64,0", Id.minecraft("chiseled_copper"));
    assertThat(PlinthStructure.valid(lookup, spot(0, 64, 0), PEDESTALS)).isFalse();

    blocks.clear();
    blocks.put("0,64,0", Id.minecraft("lodestone"));
    blocks.put("0,64,-3", Id.minecraft("chiseled_copper"));
    blocks.put("3,64,0", Id.minecraft("chiseled_copper"));
    blocks.put("0,64,3", Id.minecraft("chiseled_copper"));
    blocks.put("-3,64,0", Id.minecraft("chiseled_copper"));
    assertThat(PlinthStructure.valid(lookup, spot(0, 64, 0), PEDESTALS)).isFalse();

    blocks.clear();
    blocks.put("0,64,0", Id.minecraft("lodestone"));
    blocks.put("0,65,-2", Id.minecraft("chiseled_copper"));
    blocks.put("2,65,0", Id.minecraft("chiseled_copper"));
    blocks.put("0,65,2", Id.minecraft("chiseled_copper"));
    blocks.put("-2,65,0", Id.minecraft("chiseled_copper"));
    assertThat(PlinthStructure.valid(lookup, spot(0, 64, 0), PEDESTALS)).isFalse();
  }

  @Test
  void blocksInUnloadedChunksNeverFormAPlinth() {
    build(0, 64, 0, Id.minecraft("chiseled_copper"));
    PlinthStructure.Blocks partial = (x, y, z) -> x > 1 ? null : lookup.type(x, y, z);

    assertThat(PlinthStructure.valid(partial, spot(0, 64, 0), PEDESTALS)).isFalse();
    assertThat(PlinthStructure.locate(partial, spot(0, 64, 0), PEDESTALS)).isEmpty();
  }

  @Test
  void aConfiguredPedestalListReplacesTheDefault() {
    build(0, 64, 0, Id.minecraft("chiseled_copper"));
    Set<Id> polished = PlinthStructure.pedestals(List.of("POLISHED_ANDESITE"), material -> true, message -> {
    });

    assertThat(PlinthStructure.valid(lookup, spot(0, 64, 0), polished)).isFalse();
    build(0, 64, 0, Id.minecraft("polished_andesite"));
    assertThat(PlinthStructure.valid(lookup, spot(0, 64, 0), polished)).isTrue();
  }

  @Test
  void locatingFromTheCenterOrAnyPedestalFindsTheSamePlinth() {
    build(5, 64, 5, Id.minecraft("chiseled_copper"));
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
    build(5, 64, 5, Id.minecraft("chiseled_copper"));
    blocks.put("9,64,9", Id.minecraft("chiseled_copper"));
    blocks.put("20,64,20", Id.minecraft("lodestone"));

    assertThat(PlinthStructure.locate(lookup, spot(9, 64, 9), PEDESTALS)).isEmpty();
    assertThat(PlinthStructure.locate(lookup, spot(20, 64, 20), PEDESTALS)).isEmpty();
    assertThat(PlinthStructure.locate(lookup, spot(6, 64, 5), PEDESTALS)).isEmpty();
  }

  @Test
  void aSharedPedestalBelongsToTheFirstValidPlinth() {
    build(0, 64, 0, Id.minecraft("chiseled_copper"));
    build(4, 64, 0, Id.minecraft("chiseled_copper"));

    assertThat(PlinthStructure.locate(lookup, spot(2, 64, 0), PEDESTALS))
        .contains(new PlinthStructure.PlinthTarget(spot(0, 64, 0), PlinthSlot.EAST));
  }

  @Test
  void plinthPartsAreLodestonesAndPedestalBlocks() {
    assertThat(PlinthStructure.isPart(Id.minecraft("lodestone"), PEDESTALS)).isTrue();
    assertThat(PlinthStructure.isPart(Id.minecraft("waxed_chiseled_copper"), PEDESTALS)).isTrue();
    assertThat(PlinthStructure.isPart(Id.minecraft("copper_block"), PEDESTALS)).isFalse();
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

  private void build(int x, int y, int z, Id pedestal) {
    blocks.put(x + "," + y + "," + z, Id.minecraft("lodestone"));
    for (PlinthSlot slot : PlinthSlot.PEDESTALS) {
      blocks.put((x + slot.dx()) + "," + y + "," + (z + slot.dz()), pedestal);
    }
  }

  private static Set<Id> withBlockRegistry(Supplier<Set<Id>> parse) {
    AdaptCore previous = AdaptCore.current();
    AdaptCore core = TestCores.install();
    RegistryLookup registries = mock(RegistryLookup.class);
    when(core.getPlatform().host().registries()).thenReturn(registries);
    when(registries.exists(eq(RegistryKind.BLOCK), any(Id.class))).thenAnswer(invocation -> KNOWN_BLOCKS.contains(invocation.<Id>getArgument(1).path()));
    try {
      return parse.get();
    } finally {
      TestCores.use(previous);
    }
  }

  private static PlinthSpot spot(int x, int y, int z) {
    return new PlinthSpot(WORLD, x, y, z);
  }
}
