package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.GolemTaskBoard;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnglerRulesTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000027b1");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000027b2");
  private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-0000000027b3");
  private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-0000000027b4");
  private static final NodeKey KEY = new NodeKey(WORLD, 0, 64, 0, BlockFace.NORTH);
  private static final NodeKey OTHER = new NodeKey(WORLD, 6, 64, 6, BlockFace.SOUTH);
  private static final NodeKey HARVEST = new NodeKey(WORLD, 9, 64, 9, BlockFace.UP);
  private static final long NOW = 50_000L;
  private static final long LEASE = 20_000L;

  private final GolemAnglerCore.Config config = new GolemAnglerCore.Config();

  @Test
  void catchTimesAndLuckFollowTheLevel() {
    assertThat(AnglerRules.catchSeconds(config.catchIntervalSecondsByLevel, 1)).isEqualTo(60);
    assertThat(AnglerRules.catchSeconds(config.catchIntervalSecondsByLevel, 5)).isEqualTo(25);
    assertThat(AnglerRules.catchSeconds(config.catchIntervalSecondsByLevel, 0)).isEqualTo(60);
    assertThat(AnglerRules.catchSeconds(List.of(0, -4), 2)).isEqualTo(1);
    assertThat(AnglerRules.luck(config.luckByLevel, 2)).isZero();
    assertThat(AnglerRules.luck(config.luckByLevel, 3)).isEqualTo(1);
    assertThat(AnglerRules.luck(config.luckByLevel, 5)).isEqualTo(2);
    assertThat(AnglerRules.luck(List.of(-3), 1)).isZero();
  }

  @Test
  void catchIntervalsConvertToTicksAndShrinkInTheRain() {
    assertThat(AnglerRules.intervalTicks(60, 1D)).isEqualTo(1200);
    assertThat(AnglerRules.intervalTicks(32, 0.75D)).isEqualTo(480);
    assertThat(AnglerRules.intervalTicks(1, 0.01D)).isEqualTo(1);
    assertThat(AnglerRules.rainMultiplier(0D)).isEqualTo(0.05D);
    assertThat(AnglerRules.rainMultiplier(2D)).isEqualTo(1D);
    assertThat(AnglerRules.rainMultiplier(Double.NaN)).isEqualTo(1D);
    assertThat(AnglerRules.rainMultiplier(0.75D)).isEqualTo(0.75D);
  }

  @Test
  void aNodeOffersOneSeatPerAllowedAnglerUpToFive() {
    assertThat(AnglerRules.seats(2)).isEqualTo(2);
    assertThat(AnglerRules.seats(0)).isEqualTo(1);
    assertThat(AnglerRules.seats(9)).isEqualTo(AnglerWater.MAX_SEATS);
  }

  @Test
  void seatsAreRecognizedFromTheJobTarget() {
    assertThat(AnglerRules.seatOf(fish(KEY, AnglerWater.seat(KEY, 0)), KEY, 2)).isZero();
    assertThat(AnglerRules.seatOf(fish(KEY, AnglerWater.seat(KEY, 1)), KEY, 2)).isEqualTo(1);
    assertThat(AnglerRules.seatOf(fish(KEY, AnglerWater.seat(KEY, 2)), KEY, 2)).isEqualTo(-1);
    assertThat(AnglerRules.seatOf(fish(KEY, new int[]{0, 64, -3}), KEY, 2)).isEqualTo(-1);
  }

  @Test
  void fishersCountLiveFishingLeasesOfOtherGolems() {
    GolemTaskBoard board = new GolemTaskBoard(WORLD, key -> Optional.of(NodeRecord.builder(OWNER, NodeType.FISHING, key.face()).build()));
    GolemTask first = fish(KEY, AnglerWater.seat(KEY, 0));
    GolemTask second = fish(OTHER, AnglerWater.seat(OTHER, 0));
    GolemTask open = fish(KEY, AnglerWater.seat(KEY, 1));
    GolemTask harvest = GolemTask.builder(HARVEST, NodeType.HARVEST, AnglerRules.FISH, GolemTarget.block(WORLD, 9, 65, 9)).build();
    for (GolemTask task : List.of(first, second, open, harvest)) {
      board.offer(task, 16);
    }
    board.reserve(first, FIRST, NOW, LEASE);
    board.reserve(second, SECOND, NOW, LEASE);
    board.reserve(harvest, SECOND, NOW, LEASE);
    List<GolemTask> tasks = board.chunkTasks(KEY.chunkKey());

    assertThat(AnglerRules.fishers(tasks, null, NOW)).isEqualTo(2);
    assertThat(AnglerRules.fishers(tasks, FIRST, NOW)).isEqualTo(1);
    assertThat(AnglerRules.fishers(tasks, null, NOW + LEASE)).isZero();
    assertThat(AnglerRules.openSeats(tasks, NOW)).isEqualTo(1);
    assertThat(AnglerRules.openSeats(tasks, NOW + LEASE)).isEqualTo(3);
  }

  @Test
  void theJunkFilterAppliesOnlyFromItsLevelAndTogglesWithoutTouchingOtherSettings() {
    NodeRecord plain = NodeRecord.builder(OWNER, NodeType.FISHING, BlockFace.NORTH).data(Map.of("other", "x")).build();
    NodeRecord filtered = AnglerRules.toggleJunk(plain);

    assertThat(AnglerRules.discardJunk(config, 3, plain)).isFalse();
    assertThat(AnglerRules.discardJunk(config, 2, filtered)).isFalse();
    assertThat(AnglerRules.discardJunk(config, 3, filtered)).isTrue();
    assertThat(filtered.data()).containsEntry("other", "x");
    assertThat(AnglerRules.toggleJunk(filtered).data()).containsExactly(Map.entry("other", "x"));
  }

  @Test
  void loadedValuesAreClamped() {
    assertThat(GolemancyRules.fraction(1.5D)).isEqualTo(1D);
    assertThat(GolemancyRules.fraction(-0.2D)).isZero();
    assertThat(GolemancyRules.fraction(Double.NaN)).isZero();
    assertThat(GolemancyRules.nonNegative(-6D)).isZero();
    assertThat(AnglerRules.atLeastOne(0)).isEqualTo(1);
    assertThat(AnglerRules.atLeastOne(7)).isEqualTo(7);
  }

  @Test
  void theConfigCategoryWeightsFormTheLuckWeights() {
    AnglerLoot.Weights weights = AnglerRules.weights(config);

    assertThat(weights.fish()).isEqualTo(new AnglerLoot.Weight(85, -1));
    assertThat(weights.junk()).isEqualTo(new AnglerLoot.Weight(10, -2));
    assertThat(weights.treasure()).isEqualTo(new AnglerLoot.Weight(5, 2));
  }

  private static GolemTask fish(NodeKey key, int[] at) {
    return GolemTask.builder(key, NodeType.FISHING, AnglerRules.FISH, GolemTarget.block(WORLD, at[0], at[1], at[2])).build();
  }
}
