package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeCuboid;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AreaSelectionsTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final UUID OTHER_WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
  private static final NodeKey KEY = new NodeKey(WORLD, 10, 64, 10, BlockFace.UP);
  private static final NodeKey OTHER_KEY = new NodeKey(WORLD, 40, 64, 10, BlockFace.UP);
  private static final NodeRecord RECORD = NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).build();
  private static final AreaSelections.Watch HOLDING = new AreaSelections.Watch(WORLD, true, true);

  private final AreaSelections selections = new AreaSelections();

  @Test
  void onlyTheNodesOwnerCanStartSelecting() {
    assertThat(selections.begin(STRANGER, KEY, RECORD, 0L)).isFalse();
    assertThat(selections.selection(STRANGER)).isEmpty();
    assertThat(selections.swallows(STRANGER, 0L)).isFalse();

    assertThat(selections.begin(OWNER, KEY, RECORD, 0L)).isTrue();
    assertThat(selections.selection(OWNER)).map(AreaSelections.Selection::node).contains(KEY);
    assertThat(selections.swallows(OWNER, 0L)).isTrue();
  }

  @Test
  void theCornersAreSetByTheirOwnClicksAndFormTheCuboidAroundTheCenter() {
    selections.begin(OWNER, KEY, RECORD, 0L);

    AreaSelections.Selection first = selections.corner(OWNER, AreaSelections.Corner.FIRST, 13, 64, 8, 10L).orElseThrow();
    assertThat(first.complete()).isFalse();
    AreaSelections.Selection both = selections.corner(OWNER, AreaSelections.Corner.SECOND, 9, 67, 11, 20L).orElseThrow();

    assertThat(both.complete()).isTrue();
    assertThat(both.area()).isEqualTo(new NodeCuboid(-1, -1, -2, 3, 2, 1));
    AreaSelections.Selection moved = selections.corner(OWNER, AreaSelections.Corner.FIRST, 10, 65, 10, 30L).orElseThrow();
    assertThat(moved.area()).isEqualTo(new NodeCuboid(-1, 0, 0, 0, 2, 1));
  }

  @Test
  void applyingAValidSelectionHandsBackItsAreaAndEndsTheMode() {
    selections.begin(OWNER, KEY, RECORD, 0L);
    selections.corner(OWNER, AreaSelections.Corner.FIRST, 18, 65, 10, 10L);
    selections.corner(OWNER, AreaSelections.Corner.SECOND, 10, 66, 12, 20L);

    AreaSelections.Applied applied = selections.apply(OWNER, 8, 30L);

    assertThat(applied.outcome()).isEqualTo(AreaSelections.Outcome.READY);
    assertThat(applied.node()).isEqualTo(KEY);
    assertThat(applied.area()).isEqualTo(new NodeCuboid(0, 0, 0, 8, 1, 2));
    assertThat(selections.selection(OWNER)).isEmpty();
    assertThat(selections.apply(OWNER, 8, 40L).outcome()).isEqualTo(AreaSelections.Outcome.NOT_SELECTING);
  }

  @Test
  void applyingWithAMissingCornerKeepsTheModeOpen() {
    selections.begin(OWNER, KEY, RECORD, 0L);
    selections.corner(OWNER, AreaSelections.Corner.SECOND, 12, 65, 12, 10L);

    assertThat(selections.apply(OWNER, 8, 20L).outcome()).isEqualTo(AreaSelections.Outcome.INCOMPLETE);
    assertThat(selections.selection(OWNER)).isPresent();
  }

  @Test
  void anInvalidSelectionIsRejectedWithItsFailedLimitAndTheModeStaysOpen() {
    selections.begin(OWNER, KEY, RECORD, 0L);
    selections.corner(OWNER, AreaSelections.Corner.FIRST, 12, 65, 10, 10L);
    selections.corner(OWNER, AreaSelections.Corner.SECOND, 20, 66, 12, 20L);

    AreaSelections.Applied outside = selections.apply(OWNER, 16, 30L);
    assertThat(outside.outcome()).isEqualTo(AreaSelections.Outcome.REJECTED);
    assertThat(outside.limit().verdict()).isEqualTo(NodeCuboid.Verdict.MISSING_CENTER);

    selections.corner(OWNER, AreaSelections.Corner.FIRST, 1, 65, 10, 40L);
    AreaSelections.Applied tooFar = selections.apply(OWNER, 8, 50L);
    assertThat(tooFar.outcome()).isEqualTo(AreaSelections.Outcome.REJECTED);
    assertThat(tooFar.limit().verdict()).isEqualTo(NodeCuboid.Verdict.TOO_FAR);
    assertThat(tooFar.limit().face()).isEqualTo("+X");
    assertThat(tooFar.limit().distance()).isEqualTo(10);
    assertThat(selections.selection(OWNER)).isPresent();
  }

  @Test
  void cancellingEndsTheModeButSwallowsTheClicksEcho() {
    selections.begin(OWNER, KEY, RECORD, 0L);

    assertThat(selections.end(OWNER, 1_000L)).isPresent();

    assertThat(selections.selection(OWNER)).isEmpty();
    assertThat(selections.swallows(OWNER, 1_000L + AreaSelections.ECHO_MILLIS)).isTrue();
    assertThat(selections.swallows(OWNER, 1_001L + AreaSelections.ECHO_MILLIS)).isFalse();
    assertThat(selections.end(OWNER, 2_000L)).isEmpty();
  }

  @Test
  void startingAnotherNodeReplacesTheFirstSelection() {
    selections.begin(OWNER, KEY, RECORD, 0L);
    selections.corner(OWNER, AreaSelections.Corner.FIRST, 12, 65, 10, 10L);

    selections.begin(OWNER, OTHER_KEY, RECORD, 20L);

    AreaSelections.Selection current = selections.selection(OWNER).orElseThrow();
    assertThat(current.node()).isEqualTo(OTHER_KEY);
    assertThat(current.first()).isNull();
  }

  @Test
  void theModeTimesOutAfterTwoMinutesWithoutAClick() {
    selections.begin(OWNER, KEY, RECORD, 0L);
    selections.corner(OWNER, AreaSelections.Corner.FIRST, 12, 65, 10, 5_000L);

    assertThat(selections.watch(OWNER, HOLDING, 5_000L + AreaSelections.IDLE_MILLIS - 1L)).isEqualTo(AreaSelections.Exit.NONE);
    assertThat(selections.watch(OWNER, HOLDING, 5_000L + AreaSelections.IDLE_MILLIS)).isEqualTo(AreaSelections.Exit.TIMED_OUT);
    assertThat(selections.selection(OWNER)).isEmpty();
  }

  @Test
  void puttingTheCrookAwayForMoreThanAMomentEndsTheMode() {
    AreaSelections.Watch away = new AreaSelections.Watch(WORLD, false, true);
    selections.begin(OWNER, KEY, RECORD, 0L);

    assertThat(selections.watch(OWNER, away, 1_000L)).isEqualTo(AreaSelections.Exit.NONE);
    assertThat(selections.watch(OWNER, HOLDING, 1_500L)).isEqualTo(AreaSelections.Exit.NONE);
    assertThat(selections.watch(OWNER, away, 2_000L)).isEqualTo(AreaSelections.Exit.NONE);
    assertThat(selections.watch(OWNER, away, 2_000L + AreaSelections.AWAY_MILLIS - 1L)).isEqualTo(AreaSelections.Exit.NONE);
    assertThat(selections.watch(OWNER, away, 2_000L + AreaSelections.AWAY_MILLIS)).isEqualTo(AreaSelections.Exit.PUT_AWAY);
    assertThat(selections.selection(OWNER)).isEmpty();
  }

  @Test
  void theModeEndsWhenTheNodeIsRemoved() {
    selections.begin(OWNER, KEY, RECORD, 0L);

    assertThat(selections.watch(OWNER, new AreaSelections.Watch(WORLD, true, false), 10L)).isEqualTo(AreaSelections.Exit.NODE_GONE);
    assertThat(selections.selection(OWNER)).isEmpty();
  }

  @Test
  void theModeEndsWhenThePlayerLeavesTheNodesWorld() {
    selections.begin(OWNER, KEY, RECORD, 0L);

    assertThat(selections.watch(OWNER, new AreaSelections.Watch(OTHER_WORLD, true, true), 10L)).isEqualTo(AreaSelections.Exit.WORLD_CHANGED);
    assertThat(selections.selection(OWNER)).isEmpty();
  }

  @Test
  void quittingForgetsTheSelectionAndItsEcho() {
    selections.begin(OWNER, KEY, RECORD, 0L);

    selections.forget(OWNER);

    assertThat(selections.selection(OWNER)).isEmpty();
    assertThat(selections.swallows(OWNER, 0L)).isFalse();
    assertThat(selections.watch(OWNER, HOLDING, 10L)).isEqualTo(AreaSelections.Exit.NONE);
  }

  @Test
  void eachSelectionCarriesItsOwnSessionSoAStaleWatchStops() {
    selections.begin(OWNER, KEY, RECORD, 0L);
    long first = selections.selection(OWNER).orElseThrow().session();

    selections.begin(OWNER, KEY, RECORD, 10L);

    assertThat(selections.selection(OWNER).orElseThrow().session()).isNotEqualTo(first);
  }
}
