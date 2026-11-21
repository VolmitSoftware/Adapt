package art.arcane.adapt.util.common.input;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.platform.event.player.PlayerInput;
import art.arcane.adapt.platform.event.player.PlayerToggleFlightEvent;
import art.arcane.adapt.testkit.FakeGameContent;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.common.math.PlayerInputPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The double jump state machine, one call per player move, as the three input sources feed it: the player input packet
 * (1.21.2+), the Adapt client's jump key reports and vanilla flight requests (both before 1.21.2).
 */
class DoubleJumpGestureTest {
  private static final int AIRTIME_MOVES = 12;

  @TempDir
  Path dataFolder;

  private boolean jumpKey;
  private boolean reportLate;

  @AfterEach
  void tearDown() {
    AdaptPorts.uninstall(PlayerInputPort.class);
  }

  @ParameterizedTest
  @ValueSource(ints = {3, 5, 8, 11})
  void inputPacketJumpThenOneMidairPressCompletesAtAnyTiming(int pressMove) {
    withInput(false, (player, gesture) -> {
      assertThat(jumpAndPress(player, gesture, pressMove, false)).containsExactly(pressMove);
    });
  }

  @Test
  void inputPacketSprintJumpCountsTheSame() {
    withInput(false, (player, gesture) -> {
      player.setSprinting(true);
      assertThat(jumpAndPress(player, gesture, 4, false)).containsExactly(4);
    });
  }

  @Test
  void inputPacketSingleJumpAndLandingNeverComplete() {
    withInput(false, (player, gesture) -> {
      assertThat(jumpAndPress(player, gesture, -1, false)).isEmpty();
      assertThat(jumpAndPress(player, gesture, -1, true)).isEmpty();
    });
  }

  @Test
  void inputPacketWalkingOffAnEdgeNeedsTwoMidairPresses() {
    withInput(false, (player, gesture) -> {
      ground(player, gesture);
      player.setOnGround(false);
      assertThat(move(gesture, player, false)).isFalse();
      assertThat(move(gesture, player, true)).isFalse();
      assertThat(move(gesture, player, false)).isFalse();
      assertThat(move(gesture, player, true)).isTrue();
    });
  }

  @Test
  void inputPacketJumpHeldThroughALandingIsTheFirstPressOfTheNextJump() {
    withInput(false, (player, gesture) -> {
      ground(player, gesture);
      assertThat(move(gesture, player, true)).isFalse();
      player.setOnGround(false);
      assertThat(move(gesture, player, true)).isFalse();
      player.setOnGround(true);
      assertThat(move(gesture, player, true)).isFalse();
      player.setOnGround(false);
      assertThat(move(gesture, player, true)).isFalse();
      assertThat(move(gesture, player, false)).isFalse();
      assertThat(move(gesture, player, true)).isTrue();
    });
  }

  @Test
  void inputPacketNeverCompletesWhileFlyingGlidingOrAllowedToFly() {
    withInput(false, (player, gesture) -> {
      player.setAllowFlight(true);
      assertThat(jumpAndPress(player, gesture, 3, false)).isEmpty();
      player.setFlying(true);
      assertThat(jumpAndPress(player, gesture, 3, false)).isEmpty();
      player.setAllowFlight(false);
      player.setGliding(true);
      assertThat(jumpAndPress(player, gesture, 3, false)).isEmpty();
      player.setGliding(false);
      assertThat(jumpAndPress(player, gesture, 3, false)).containsExactly(3);
    });
  }

  @ParameterizedTest
  @ValueSource(ints = {3, 5, 8, 11})
  void reportedKeyCompletesOnTheSecondPressLikeTheInputPacket(int pressMove) {
    withInput(true, (player, gesture) -> {
      ReportedJumps.reporting(player.uuid(), true);
      assertThat(jumpAndPress(player, gesture, pressMove, false)).containsExactly(pressMove);
      assertThat(player.mayFly()).isFalse();
    });
  }

  @Test
  void reportedKeyArrivingAfterItsMoveCompletesOneMoveLater() {
    withInput(true, (player, gesture) -> {
      ReportedJumps.reporting(player.uuid(), true);
      reportLate = true;
      assertThat(jumpAndPress(player, gesture, 5, false)).containsExactly(6);
    });
  }

  @Test
  void reportedTapBetweenTwoMovesIsSeenByEveryGesture() {
    withInput(true, (player, first) -> {
      DoubleJumpGesture second = new DoubleJumpGesture();
      ReportedJumps.reporting(player.uuid(), true);
      first.update(player);
      second.update(player);
      player.setOnGround(false);
      ReportedJumps.report(player.uuid(), true);
      ReportedJumps.report(player.uuid(), false);
      assertThat(first.update(player)).isFalse();
      assertThat(second.update(player)).isFalse();
      ReportedJumps.report(player.uuid(), true);
      ReportedJumps.report(player.uuid(), false);
      assertThat(first.update(player)).isTrue();
      assertThat(second.update(player)).isTrue();
    });
  }

  @Test
  void reportedSingleJumpAndWalkingOffAnEdgeWithOnePressNeverComplete() {
    withInput(true, (player, gesture) -> {
      ReportedJumps.reporting(player.uuid(), true);
      assertThat(jumpAndPress(player, gesture, -1, false)).isEmpty();
      ground(player, gesture);
      player.setOnGround(false);
      gesture.update(player);
      ReportedJumps.report(player.uuid(), true);
      assertThat(gesture.update(player)).isFalse();
      ReportedJumps.report(player.uuid(), false);
      assertThat(gesture.update(player)).isFalse();
    });
  }

  @Test
  void reportingClientsEndTheFlightRequestGrant() {
    withInput(true, (player, gesture) -> {
      gesture.update(player);
      assertThat(player.mayFly()).isTrue();

      ReportedJumps.reporting(player.uuid(), true);
      gesture.update(player);

      assertThat(player.mayFly()).isFalse();
    });
  }

  @Test
  void flightRequestPathTreatsTheGroundPressAsTheFirstOfTheDoubleTap() {
    withInput(true, (player, gesture) -> {
      assertThat(gesture.update(player)).isFalse();
      assertThat(player.mayFly()).isTrue();
      player.setOnGround(false);
      assertThat(gesture.update(player)).isFalse();

      PlayerToggleFlightEvent request = new PlayerToggleFlightEvent(player, true);
      MidairJumps.active().on(request);

      assertThat(request.isCancelled()).isTrue();
      assertThat(gesture.update(player)).isTrue();
      assertThat(gesture.update(player)).isFalse();
    });
  }

  @Test
  void restartAfterATeleportKeepsTheGrant() {
    withInput(true, (player, gesture) -> {
      gesture.update(player);
      gesture.restart(player.uuid());
      assertThat(player.mayFly()).isTrue();
      gesture.reset(player);
      assertThat(player.mayFly()).isFalse();
    });
  }

  /**
   * Stands, presses jump on the ground and takes off, holds it 2 moves, then presses again {@code pressMove} moves
   * after takeoff (none when negative) and lands after {@link #AIRTIME_MOVES} moves. Reported keys reach the server
   * just before the move they belong to, as the Adapt client sends them, or just after it with {@link #reportLate}.
   * Returns the moves after takeoff on which the gesture completed.
   */
  private List<Integer> jumpAndPress(FakePlayer player, DoubleJumpGesture gesture, int pressMove, boolean holdOnLanding) {
    List<Integer> completed = new ArrayList<>();
    ground(player, gesture);
    player.setOnGround(false);
    for (int index = 0; index <= AIRTIME_MOVES; index++) {
      boolean down = index < 2 || pressMove >= 0 && index >= pressMove && index < pressMove + 2
          || holdOnLanding && index == AIRTIME_MOVES;
      if (index == AIRTIME_MOVES) {
        player.setOnGround(true);
      }
      if (move(gesture, player, down)) {
        completed.add(index);
      }
    }
    move(gesture, player, false);
    return completed;
  }

  private void ground(FakePlayer player, DoubleJumpGesture gesture) {
    player.setOnGround(true);
    move(gesture, player, false);
  }

  private boolean move(DoubleJumpGesture gesture, FakePlayer player, boolean down) {
    if (ReportedJumps.reporting(player.uuid())) {
      if (!reportLate) {
        ReportedJumps.report(player.uuid(), down);
        return gesture.update(player);
      }
      boolean completed = gesture.update(player);
      ReportedJumps.report(player.uuid(), down);
      return completed;
    }
    jumpKey = down;
    return gesture.update(player);
  }

  private void withInput(boolean legacy, GestureCheck check) {
    TestPlatform platform = TestPlatform.builder(dataFolder).build();
    platform.host().runGame(legacy ? FakeGameContent.v1_20_1() : FakeGameContent.v26_3());
    if (!legacy) {
      AdaptPorts.install(PlayerInputPort.class, player -> new PlayerInput(false, false, false, false, jumpKey, false,
          player.isSprinting()));
    }
    try (TestAdapt adapt = TestAdapt.start(platform, List.of())) {
      FakePlayer player = platform.player("Jumper", 0.5, 64, 0.5);
      try {
        check.run(player, new DoubleJumpGesture());
      } finally {
        ReportedJumps.reporting(player.uuid(), false);
      }
    }
  }

  @FunctionalInterface
  private interface GestureCheck {
    void run(FakePlayer player, DoubleJumpGesture gesture);
  }
}
