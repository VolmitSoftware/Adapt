package art.arcane.adapt.api.golem;

import org.bukkit.DyeColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GolemTaskMatchTest {
  @Test
  void aNodeWithoutAChannelAcceptsEveryGolem() {
    assertThat(GolemTaskMatch.channel(null, ChannelMask.NONE)).isTrue();
    assertThat(GolemTaskMatch.channel(null, ChannelMask.of(DyeColor.RED))).isTrue();
    assertThat(GolemTaskMatch.channel(null, ChannelMask.ALL)).isTrue();
  }

  @Test
  void aGolemWithoutAChannelAcceptsOnlyNodesWithoutOne() {
    assertThat(GolemTaskMatch.channel(DyeColor.RED, ChannelMask.NONE)).isFalse();
  }

  @Test
  void aChannelledNodeNeedsTheGolemOnThatChannel() {
    assertThat(GolemTaskMatch.channel(DyeColor.RED, ChannelMask.of(DyeColor.RED))).isTrue();
    assertThat(GolemTaskMatch.channel(DyeColor.RED, ChannelMask.of(DyeColor.BLUE, DyeColor.RED))).isTrue();
    assertThat(GolemTaskMatch.channel(DyeColor.RED, ChannelMask.of(DyeColor.BLUE))).isFalse();
  }

  @ParameterizedTest
  @CsvSource({
      "GUARD, GUARDIAN",
      "COLLECTION, GATHERER",
      "HARVEST, HARVESTER",
      "LUMBER, LUMBER",
      "BREAKER, BREAKER",
      "PASTURE, RANCHER",
      "INPUT, SORTER",
      "FILTER, SORTER",
      "FISHING, ANGLER"
  })
  void eachTaskNodeIsWorkedByItsCore(NodeType node, GolemCoreType core) {
    assertThat(GolemTaskMatch.core(node, List.of(core))).isTrue();
    for (GolemCoreType other : GolemCoreType.values()) {
      if (other != core) {
        assertThat(GolemTaskMatch.core(node, List.of(other))).as(node + " with " + other).isFalse();
      }
    }
  }

  @Test
  void depositNodesHandOutNoTasks() {
    for (GolemCoreType core : GolemCoreType.values()) {
      assertThat(GolemTaskMatch.core(NodeType.DEPOSIT, List.of(core))).isFalse();
    }
  }

  @Test
  void anyOfAGolemsCoresCanWorkTheNode() {
    assertThat(GolemTaskMatch.core(NodeType.HARVEST, List.of(GolemCoreType.GUARDIAN, GolemCoreType.HARVESTER))).isTrue();
    assertThat(GolemTaskMatch.consumer(NodeType.HARVEST, List.of(GolemCoreType.GUARDIAN, GolemCoreType.HARVESTER))).contains(GolemCoreType.HARVESTER);
    assertThat(GolemTaskMatch.consumer(NodeType.HARVEST, List.of(GolemCoreType.GUARDIAN))).isEmpty();
  }
}
