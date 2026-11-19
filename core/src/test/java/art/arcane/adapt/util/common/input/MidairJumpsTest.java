package art.arcane.adapt.util.common.input;

import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.player.PlayerChangedWorldEvent;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerGameModeChangeEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerRespawnEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleFlightEvent;
import art.arcane.adapt.testkit.FakeGameContent;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class MidairJumpsTest {
  private final AtomicLong clock = new AtomicLong(1_000L);
  private final Object owner = new Object();

  @TempDir
  Path dataFolder;

  private TestPlatform platform;
  private MidairJumps jumps;
  private FakePlayer player;

  @BeforeEach
  void setUp() {
    platform = TestPlatform.builder(dataFolder).build();
    platform.host().runGame(FakeGameContent.v1_20_1());
    jumps = new MidairJumps(clock::get);
    player = platform.player("Jumper", 0.5, 64, 0.5);
  }

  @AfterEach
  void tearDown() {
    MidairJumps.stop();
  }

  @Test
  void grantsOnlyWhileAirborneAndEndsOnLanding() {
    jumps.listen(player, owner);
    assertThat(player.mayFly()).isFalse();

    takeOff();
    assertThat(player.mayFly()).isTrue();
    assertThat(player.allowFlight()).isFalse();
    assertThat(player.data().has(MidairJumps.GRANT_MARKER)).isTrue();

    land();
    assertThat(player.mayFly()).isFalse();
    assertThat(player.flightRequestsGranted()).isFalse();
    assertThat(player.data().has(MidairJumps.GRANT_MARKER)).isFalse();
  }

  @Test
  void flightRequestIsCancelledAndCountedAsOnePress() {
    long before = takeOff();

    PlayerToggleFlightEvent request = flightRequest();
    jumps.on(request);

    assertThat(request.isCancelled()).isTrue();
    assertThat(player.isFlying()).isFalse();
    assertThat(jumps.listen(player, owner)).isEqualTo(before + 1);
  }

  @Test
  void landingWithoutAnotherListenStillEndsTheGrant() {
    takeOff();
    player.setOnGround(true);

    jumps.on(move());

    assertThat(player.mayFly()).isFalse();
  }

  @Test
  void neverTouchesMayFlyThePlayerAlreadyHad() {
    player.setAllowFlight(true);
    takeOff();
    assertThat(player.flightRequestsGranted()).isFalse();

    PlayerToggleFlightEvent request = flightRequest();
    jumps.on(request);
    land();

    assertThat(request.isCancelled()).isFalse();
    assertThat(player.mayFly()).isTrue();
  }

  @Test
  void creativeAndSpectatorAreLeftAlone() {
    player.setGameMode(GameMode.CREATIVE);
    takeOff();
    assertThat(player.flightRequestsGranted()).isFalse();

    player.setGameMode(GameMode.SPECTATOR);
    jumps.listen(player, owner);
    assertThat(player.flightRequestsGranted()).isFalse();
  }

  @Test
  void flightSetByAnotherOwnerReplacesTheGrantAndIsNeverRevoked() {
    takeOff();
    player.setAllowFlight(true);
    player.setFlying(true);

    land();

    assertThat(player.mayFly()).isTrue();
    assertThat(player.isFlying()).isTrue();
    player.setAllowFlight(false);
    assertThat(player.mayFly()).isFalse();
  }

  @Test
  void grantEndsWhenNoGestureListensAnymore() {
    takeOff();
    clock.addAndGet(MidairJumps.LISTEN_TIMEOUT_MILLIS + 1L);

    jumps.on(move());

    assertThat(player.mayFly()).isFalse();
    assertThat(jumps.granted(player.uuid())).isFalse();
  }

  @Test
  void grantEndsWhenTheLastListenerStops() {
    Object other = new Object();
    takeOff();
    jumps.listen(player, other);

    jumps.stopListening(player.uuid(), owner);
    assertThat(player.mayFly()).isTrue();

    jumps.stopListening(player.uuid(), other);
    assertThat(player.mayFly()).isFalse();
  }

  @Test
  void teleportWorldChangeGameModeDeathAndRespawnEndTheGrantUntilTheNextAirborneMove() {
    List<Runnable> endings = List.of(
        () -> jumps.on(new PlayerTeleportEvent(player, player.location(), player.location().add(10, 0, 0), TeleportCause.PLUGIN)),
        () -> jumps.on(new PlayerChangedWorldEvent(player, player.world())),
        () -> jumps.on(new PlayerGameModeChangeEvent(player, GameMode.ADVENTURE)),
        () -> jumps.on(new PlayerDeathEvent(player, null, null, null, DamageCause.FALL, List.of(), 0, false, false, 0, 0, 0)),
        () -> jumps.on(new PlayerRespawnEvent(player, player.location(), false, false)));
    for (Runnable ending : endings) {
      takeOff();
      assertThat(player.mayFly()).isTrue();

      ending.run();

      assertThat(player.mayFly()).isFalse();
      assertThat(player.data().has(MidairJumps.GRANT_MARKER)).isFalse();
    }
    takeOff();
    assertThat(player.mayFly()).isTrue();
  }

  @Test
  void quitEndsTheGrantAndForgetsThePlayer() {
    takeOff();

    jumps.on(new PlayerQuitEvent(player));

    assertThat(player.mayFly()).isFalse();
    assertThat(jumps.granted(player.uuid())).isFalse();
  }

  @Test
  void joinRemovesAGrantACrashSavedWithThePlayer() {
    player.setAllowFlight(true);
    player.data().set(MidairJumps.GRANT_MARKER, (byte) 1);

    jumps.on(new PlayerJoinEvent(player));

    assertThat(player.mayFly()).isFalse();
    assertThat(player.data().has(MidairJumps.GRANT_MARKER)).isFalse();
  }

  @Test
  void joinKeepsFlightOfCreativePlayersAndPlayersWithoutTheMarker() {
    FakePlayer creative = platform.player("Builder", 3.5, 64, 0.5);
    creative.setGameMode(GameMode.CREATIVE);
    creative.setAllowFlight(true);
    creative.data().set(MidairJumps.GRANT_MARKER, (byte) 1);
    player.setAllowFlight(true);

    jumps.on(new PlayerJoinEvent(creative));
    jumps.on(new PlayerJoinEvent(player));

    assertThat(creative.mayFly()).isTrue();
    assertThat(creative.data().has(MidairJumps.GRANT_MARKER)).isFalse();
    assertThat(player.mayFly()).isTrue();
  }

  @Test
  void stopEndsEveryGrant() {
    try (TestAdapt adapt = TestAdapt.start(platform, List.of())) {
      MidairJumps running = MidairJumps.active();
      assertThat(running).isNotNull();
      player.setOnGround(false);
      running.listen(player, owner);
      assertThat(player.mayFly()).isTrue();

      MidairJumps.stop();

      assertThat(player.mayFly()).isFalse();
      assertThat(MidairJumps.active()).isNull();
    }
  }

  @Test
  void doubleJumpGestureCompletesOnAFlightRequestInMidAir() {
    try (TestAdapt adapt = TestAdapt.start(platform, List.of())) {
      DoubleJumpGesture gesture = new DoubleJumpGesture();
      assertThat(gesture.update(player)).isFalse();
      player.setOnGround(false);
      assertThat(gesture.update(player)).isFalse();
      assertThat(player.mayFly()).isTrue();

      PlayerToggleFlightEvent request = adapt.post(flightRequest());

      assertThat(request.isCancelled()).isTrue();
      assertThat(gesture.update(player)).isTrue();
      assertThat(gesture.update(player)).isFalse();

      gesture.reset(player);
      assertThat(player.mayFly()).isFalse();
    }
  }

  @Test
  void gestureStartedAfterEarlierPressesDoesNotFireOnThem() {
    try (TestAdapt adapt = TestAdapt.start(platform, List.of())) {
      DoubleJumpGesture first = new DoubleJumpGesture();
      DoubleJumpGesture second = new DoubleJumpGesture();
      player.setOnGround(false);
      first.update(player);
      adapt.post(flightRequest());

      assertThat(second.update(player)).isFalse();
      assertThat(first.update(player)).isTrue();
    }
  }

  @Test
  void serversWithPlayerInputDoNotStartTheEmulation() {
    platform.host().runGame(FakeGameContent.v26_3());
    try (TestAdapt adapt = TestAdapt.start(platform, List.of())) {
      assertThat(MidairJumps.active()).isNull();
      player.setOnGround(false);
      new DoubleJumpGesture().update(player);
      assertThat(player.mayFly()).isFalse();
    }
  }

  private long takeOff() {
    player.setOnGround(false);
    return jumps.listen(player, owner);
  }

  private void land() {
    player.setOnGround(true);
    jumps.listen(player, owner);
  }

  private PlayerMoveEvent move() {
    return new PlayerMoveEvent(player, player.location(), player.location().add(0, -0.1, 0));
  }

  private PlayerToggleFlightEvent flightRequest() {
    return new PlayerToggleFlightEvent(player, true);
  }
}
