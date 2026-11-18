package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SorterRulesTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000026a1");

  private final GolemSorterCore.Config config = new GolemSorterCore.Config();

  @Test
  void levelValuesComeFromTheirListsAndStayPositive() {
    assertThat(SorterRules.destinationRadius(config.destinationRadiusByLevel, 1)).isEqualTo(8);
    assertThat(SorterRules.destinationRadius(config.destinationRadiusByLevel, 5)).isEqualTo(24);
    assertThat(SorterRules.destinationRadius(config.destinationRadiusByLevel, 9)).isEqualTo(24);
    assertThat(SorterRules.destinationRadius(config.destinationRadiusByLevel, 0)).isEqualTo(8);
    assertThat(SorterRules.destinationRadius(List.of(), 3)).isEqualTo(1);
    assertThat(SorterRules.destinationRadius(List.of(-4), 1)).isEqualTo(1);
    assertThat(SorterRules.stacksPerTrip(config.stacksPerTripByLevel, 1)).isEqualTo(1);
    assertThat(SorterRules.stacksPerTrip(config.stacksPerTripByLevel, 3)).isEqualTo(2);
    assertThat(SorterRules.stacksPerTrip(config.stacksPerTripByLevel, 5)).isEqualTo(4);
    assertThat(SorterRules.stacksPerTrip(List.of(0), 1)).isEqualTo(1);
  }

  @Test
  void matchModesUnlockWithTheirLevels() {
    assertThat(SorterRules.highestMatch(config, 1)).isEqualTo(SorterRules.Match.EXACT);
    assertThat(SorterRules.highestMatch(config, 2)).isEqualTo(SorterRules.Match.GROUPS);
    assertThat(SorterRules.highestMatch(config, 3)).isEqualTo(SorterRules.Match.GROUPS);
    assertThat(SorterRules.highestMatch(config, 4)).isEqualTo(SorterRules.Match.PRECISE);
  }

  @Test
  void anInputNodesStoredModeIsCappedByTheLevelAndCyclesThroughUnlockedModes() {
    NodeRecord input = input().build();
    assertThat(SorterRules.match(input, config, 4)).isEqualTo(SorterRules.Match.PRECISE);
    assertThat(SorterRules.match(input, config, 1)).isEqualTo(SorterRules.Match.EXACT);

    NodeRecord precise = input.toBuilder().data(Map.of(SorterRules.MATCH, "precise")).build();
    assertThat(SorterRules.match(precise, config, 2)).isEqualTo(SorterRules.Match.GROUPS);
    NodeRecord exact = input.toBuilder().data(Map.of(SorterRules.MATCH, "exact")).build();
    assertThat(SorterRules.match(exact, config, 5)).isEqualTo(SorterRules.Match.EXACT);
    NodeRecord unknown = input.toBuilder().data(Map.of(SorterRules.MATCH, "sideways")).build();
    assertThat(SorterRules.match(unknown, config, 2)).isEqualTo(SorterRules.Match.GROUPS);

    NodeRecord groups = SorterRules.cycleMatch(exact, config, 4, true);
    assertThat(SorterRules.match(groups, config, 4)).isEqualTo(SorterRules.Match.GROUPS);
    assertThat(SorterRules.match(SorterRules.cycleMatch(groups, config, 4, true), config, 4)).isEqualTo(SorterRules.Match.PRECISE);
    assertThat(SorterRules.match(SorterRules.cycleMatch(exact, config, 4, false), config, 4)).isEqualTo(SorterRules.Match.PRECISE);
    assertThat(SorterRules.match(SorterRules.cycleMatch(groups, config, 2, true), config, 2)).isEqualTo(SorterRules.Match.EXACT);
    assertThat(SorterRules.match(SorterRules.cycleMatch(exact, config, 1, true), config, 1)).isEqualTo(SorterRules.Match.EXACT);
  }

  @Test
  void theDestinationRadiusDefaultsToTheLevelAndStepsWithinIt() {
    NodeRecord input = input().build();
    assertThat(SorterRules.radius(input, 16)).isEqualTo(16);

    NodeRecord smaller = SorterRules.resize(input, 16, -1);
    assertThat(SorterRules.radius(smaller, 16)).isEqualTo(14);
    assertThat(SorterRules.radius(SorterRules.resize(smaller, 16, 1), 16)).isEqualTo(16);
    assertThat(SorterRules.radius(SorterRules.resize(input, 16, 1), 16)).isEqualTo(16);
    assertThat(SorterRules.radius(smaller, 8)).isEqualTo(8);

    NodeRecord tiny = input.toBuilder().data(Map.of(SorterRules.RADIUS, "2")).build();
    assertThat(SorterRules.radius(SorterRules.resize(tiny, 16, -1), 16)).isEqualTo(SorterRules.MIN_RADIUS);
    NodeRecord broken = input.toBuilder().data(Map.of(SorterRules.RADIUS, "wide")).build();
    assertThat(SorterRules.radius(broken, 12)).isEqualTo(12);
  }

  @Test
  void filterNodesToggleOverflowAndStepTheirRestockTarget() {
    NodeRecord filter = NodeRecord.builder(OWNER, NodeType.FILTER, BlockFace.UP).data(Map.of("other", "kept")).build();
    assertThat(SorterRules.overflow(filter)).isFalse();
    NodeRecord overflow = SorterRules.toggleOverflow(filter);
    assertThat(SorterRules.overflow(overflow)).isTrue();
    assertThat(overflow.data()).containsEntry("other", "kept");
    assertThat(SorterRules.overflow(SorterRules.toggleOverflow(overflow))).isFalse();

    assertThat(SorterRules.restockTarget(filter)).isZero();
    NodeRecord one = SorterRules.stepRestock(filter, true);
    assertThat(SorterRules.restockTarget(one)).isEqualTo(1);
    assertThat(SorterRules.restockTarget(SorterRules.stepRestock(one, true))).isEqualTo(4);
    assertThat(SorterRules.restockTarget(SorterRules.stepRestock(one, false))).isZero();
    assertThat(SorterRules.stepRestock(one, false).data()).doesNotContainKey(SorterRules.RESTOCK_TARGET);
    assertThat(SorterRules.restockTarget(SorterRules.stepRestock(filter, false))).isZero();
    NodeRecord most = filter.toBuilder().data(Map.of(SorterRules.RESTOCK_TARGET, "512")).build();
    assertThat(SorterRules.restockTarget(SorterRules.stepRestock(most, true))).isEqualTo(512);
    NodeRecord odd = filter.toBuilder().data(Map.of(SorterRules.RESTOCK_TARGET, "10")).build();
    assertThat(SorterRules.restockTarget(SorterRules.stepRestock(odd, true))).isEqualTo(16);
    assertThat(SorterRules.restockTarget(SorterRules.stepRestock(odd, false))).isEqualTo(8);
    NodeRecord broken = filter.toBuilder().data(Map.of(SorterRules.RESTOCK_TARGET, "lots")).build();
    assertThat(SorterRules.restockTarget(broken)).isZero();
  }

  @Test
  void shulkerBoxesAndPreciseItemsAreRecognizedByType() {
    assertThat(SorterRules.shulker(Id.minecraft("shulker_box"))).isTrue();
    assertThat(SorterRules.shulker(Id.minecraft("red_shulker_box"))).isTrue();
    assertThat(SorterRules.shulker(Id.minecraft("chest"))).isFalse();
    assertThat(SorterRules.precise(Id.minecraft("enchanted_book"))).isTrue();
    assertThat(SorterRules.precise(Id.minecraft("potion"))).isTrue();
    assertThat(SorterRules.precise(Id.minecraft("splash_potion"))).isTrue();
    assertThat(SorterRules.precise(Id.minecraft("lingering_potion"))).isTrue();
    assertThat(SorterRules.precise(Id.minecraft("tipped_arrow"))).isTrue();
    assertThat(SorterRules.precise(Id.minecraft("diamond_sword"))).isFalse();
  }

  @Test
  void groupEntriesNameMaterialsAndTagsAndTheFirstGroupWins() {
    List<String> rejected = new ArrayList<>();
    Map<String, Collection<Id>> tags = Map.of("#minecraft:logs", List.of(Id.minecraft("oak_log"), Id.minecraft("birch_log")),
        "#minecraft:coal_ores", List.of(Id.minecraft("coal_ore"), Id.minecraft("deepslate_coal_ore")));

    Map<Id, String> groups = SorterRules.groups(List.of("logs=#logs", "ores = #coal_ores, IRON_ORE, oak_log", "nameless",
        "empty=", "stone=STONE,NOT_A_THING,#minecraft:missing"), tag -> tags.getOrDefault(tag, List.of()),
        id -> !id.path().equals("not_a_thing"), rejected::add);

    assertThat(groups).containsOnly(Map.entry(Id.minecraft("oak_log"), "logs"), Map.entry(Id.minecraft("birch_log"), "logs"),
        Map.entry(Id.minecraft("coal_ore"), "ores"), Map.entry(Id.minecraft("deepslate_coal_ore"), "ores"), Map.entry(Id.minecraft("iron_ore"), "ores"),
        Map.entry(Id.minecraft("stone"), "stone"));
    assertThat(rejected).containsExactly("nameless", "empty=", "NOT_A_THING", "#minecraft:missing");
  }

  @Test
  void theDefaultGroupsParseWithoutRejectedMaterials() {
    List<String> rejected = new ArrayList<>();

    Map<Id, String> groups = SorterRules.groups(config.groups, tag -> List.of(Id.minecraft("oak_log")), id -> true, name -> {
      if (!name.startsWith("#")) {
        rejected.add(name);
      }
    });

    assertThat(rejected).isEmpty();
    assertThat(groups).containsEntry(Id.minecraft("cobblestone"), "stone").containsEntry(Id.minecraft("bread"), "food")
        .containsEntry(Id.minecraft("repeater"), "redstone").containsEntry(Id.minecraft("wheat_seeds"), "seeds");
  }

  private static NodeRecord.Builder input() {
    return NodeRecord.builder(OWNER, NodeType.INPUT, BlockFace.UP);
  }
}
