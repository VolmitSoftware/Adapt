package art.arcane.adapt.api.golem.overseer;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.api.golem.GolemMode;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

class OverseerTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000501");

  private final List<OverseerReceiver> registered = new ArrayList<>();
  private final World world = mock(World.class);
  private final World elsewhere = mock(World.class);

  @AfterEach
  void tearDown() {
    for (OverseerReceiver receiver : registered) {
      Overseer.unregister(receiver);
    }
  }

  @Test
  void movementSignalsMapToGolemModesAndSicSetsNone() {
    assertThat(GolemSignal.FOLLOW.mode()).contains(GolemMode.FOLLOW);
    assertThat(GolemSignal.HOLD.mode()).contains(GolemMode.STAY);
    assertThat(GolemSignal.HOME.mode()).contains(GolemMode.HOME);
    assertThat(GolemSignal.WORK.mode()).contains(GolemMode.WORK);
    assertThat(GolemSignal.SIC.mode()).isEmpty();
  }

  @Test
  void aSignalWithoutACrookChannelReachesEveryChannelAndAChannelOnlyGolemsThatShareIt() {
    OverseerSignal open = signal(null, null);
    OverseerSignal red = signal(DyeColor.RED, null);

    assertThat(open.hears(ChannelMask.NONE)).isTrue();
    assertThat(open.hears(ChannelMask.of(DyeColor.BLUE))).isTrue();
    assertThat(red.hears(ChannelMask.of(DyeColor.RED, DyeColor.BLUE))).isTrue();
    assertThat(red.hears(ChannelMask.of(DyeColor.BLUE))).isFalse();
    assertThat(red.hears(ChannelMask.NONE)).isFalse();
  }

  @Test
  void theRadiusCountsFromTheOwnerInTheSameWorldOnly() {
    OverseerSignal signal = signal(null, null);

    assertThat(signal.inRange(new Location(world, 16D, 64D, 0D))).isTrue();
    assertThat(signal.inRange(new Location(world, 16.1D, 64D, 0D))).isFalse();
    assertThat(signal.inRange(new Location(elsewhere, 1D, 64D, 0D))).isFalse();
    assertThat(signal.sameWorld(new Location(world, 400D, 64D, 0D))).isTrue();
    assertThat(signal.sameWorld(new Location(elsewhere, 0D, 64D, 0D))).isFalse();
  }

  @Test
  void theOriginIsACopySoReceiversCannotMoveIt() {
    OverseerSignal signal = signal(null, null);

    signal.origin().add(100D, 0D, 0D);

    assertThat(signal.origin().getX()).isZero();
  }

  @Test
  void onlySignalsCarryingARallyCanClaimIt() {
    AtomicInteger claims = new AtomicInteger();

    assertThat(signal(null, null).claimRally()).isFalse();
    assertThat(signal(null, () -> claims.incrementAndGet() == 1).claimRally()).isTrue();
  }

  @Test
  void reachedGolemsGetThePulse() {
    List<Entity> pulsed = new ArrayList<>();
    Entity golem = mock(Entity.class);
    OverseerSignal signal = new OverseerSignal(OWNER, GolemSignal.FOLLOW, new Location(world, 0D, 64D, 0D), 16D, null, null, 0L, null, pulsed::add);

    signal.reached(golem);

    assertThat(pulsed).containsExactly(golem);
  }

  @Test
  void dispatchReachesEveryReceiverAndAFailingReceiverIsLoggedOnceWithoutStoppingTheOthers() {
    List<String> calls = new ArrayList<>();
    register(signal -> calls.add("first"));
    register(signal -> {
      throw new IllegalStateException("broken servants");
    });
    register(signal -> calls.add("third"));

    try (MockedStatic<Adapt> logs = mockStatic(Adapt.class)) {
      Overseer.dispatch(signal(null, null));
      Overseer.dispatch(signal(null, null));

      logs.verify(() -> Adapt.warn(anyString(), any(IllegalStateException.class)), times(1));
    }
    assertThat(calls).containsExactly("first", "third", "first", "third");
  }

  @Test
  void unregisteredReceiversHearNothing() {
    List<String> calls = new ArrayList<>();
    OverseerReceiver receiver = signal -> calls.add("heard");
    register(receiver);

    assertThat(Overseer.unregister(receiver)).isTrue();
    Overseer.dispatch(signal(null, null));

    assertThat(calls).isEmpty();
    assertThat(Overseer.unregister(receiver)).isFalse();
  }

  @Test
  void onlyTheRegisteredCommanderCanClearItself() {
    Overseer.Commander first = new IdleCommander();
    Overseer.setCommander(first);
    try {
      assertThat(Overseer.clearCommander(new IdleCommander())).isFalse();
      assertThat(Overseer.commander()).containsSame(first);
    } finally {
      assertThat(Overseer.clearCommander(first)).isTrue();
    }
    assertThat(Overseer.commander()).isEmpty();
  }

  private void register(OverseerReceiver receiver) {
    registered.add(receiver);
    Overseer.register(receiver);
  }

  private OverseerSignal signal(DyeColor channel, OverseerSignal.Rally rally) {
    return new OverseerSignal(OWNER, GolemSignal.FOLLOW, new Location(world, 0D, 64D, 0D), 16D, channel, null, 0L, rally, entity -> {
    });
  }

  private static final class IdleCommander implements Overseer.Commander {
    @Override
    public void cycle(Player player) {
    }

    @Override
    public void send(Player player) {
    }

    @Override
    public boolean filtersChannels(Player player) {
      return false;
    }
  }
}
