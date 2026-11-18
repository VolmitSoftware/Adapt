package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.api.golem.overseer.GolemSignal;
import art.arcane.adapt.api.golem.overseer.OverseerSignal;
import art.arcane.adapt.api.minion.ServantOrder;
import art.arcane.adapt.api.minion.ServantPack;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OverseerServantsTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000b01");

  private final WorldRef world = mock(WorldRef.class);
  private final WorldRef elsewhere = mock(WorldRef.class);
  private final List<MobRef> pack = new ArrayList<>();
  private final Map<MobRef, ServantOrder> orders = new HashMap<>();
  private final List<EntityRef> pulsed = new ArrayList<>();
  private final List<EntityRef> scheduled = new ArrayList<>();
  private final AtomicBoolean obeying = new AtomicBoolean(true);
  private final AtomicInteger servantChannels = new AtomicInteger(ChannelMask.NONE);
  private final ServantPack servants = new FakePack();
  private final OverseerServants receiver = new OverseerServants(owner -> obeying.get(), owner -> servantChannels.get(), () -> Optional.of(servants));
  private MockedStatic<Tasks> scheduling;

  @BeforeEach
  void setUp() {
    scheduling = mockStatic(Tasks.class);
    scheduling.when(() -> Tasks.runEntity(any(EntityRef.class), any(Runnable.class))).thenAnswer(call -> {
      scheduled.add(call.getArgument(0));
      call.<Runnable>getArgument(1).run();
      return true;
    });
  }

  @AfterEach
  void tearDown() {
    scheduling.close();
  }

  @Test
  void followReachesServantsInsideTheRadiusAndPulsesThem() {
    MobRef near = servant(at(10D, world));
    MobRef far = servant(at(17D, world));
    MobRef away = servant(at(1D, elsewhere));

    receiver.receive(signal(GolemSignal.FOLLOW, null, null));

    assertThat(orders.get(near)).isEqualTo(ServantOrder.follow());
    assertThat(orders.get(far)).isEqualTo(ServantOrder.FREE);
    assertThat(orders.get(away)).isEqualTo(ServantOrder.FREE);
    assertThat(pulsed).containsExactly(near);
  }

  @Test
  void holdAnchorsEachServantWhereItStandsAndDropsItsTarget() {
    MobRef servant = servant(at(6D, world));

    receiver.receive(signal(GolemSignal.HOLD, null, null));

    assertThat(orders.get(servant)).isEqualTo(ServantOrder.hold(at(6D, world)));
    verify(servant).setTarget(null);
  }

  @Test
  void sicCommandsEveryServantInRangeAtTheTargetUntilTheHoldEnds() {
    LivingRef target = mock(MobRef.class);
    MobRef servant = servant(at(3D, world));
    orders.put(servant, ServantOrder.follow());

    receiver.receive(signal(GolemSignal.SIC, target, null));

    assertThat(orders.get(servant)).isEqualTo(ServantOrder.follow().command(target, 25_000L));
    verify(servant).setTarget(target);
    assertThat(pulsed).containsExactly(servant);
  }

  @Test
  void aMovementSignalEndsTheSicAndDropsItsTarget() {
    LivingRef target = mock(MobRef.class);
    MobRef servant = servant(at(3D, world));
    orders.put(servant, ServantOrder.FREE.command(target, 25_000L));
    lenient().when(servant.target()).thenReturn(target);

    receiver.receive(signal(GolemSignal.FOLLOW, null, null));

    assertThat(orders.get(servant)).isEqualTo(ServantOrder.follow());
    verify(servant).setTarget(null);
  }

  @Test
  void homeAndWorkNeverReachServants() {
    MobRef servant = servant(at(3D, world));

    receiver.receive(signal(GolemSignal.HOME, null, null));
    receiver.receive(signal(GolemSignal.WORK, null, null));

    assertThat(scheduled).isEmpty();
    assertThat(orders.get(servant)).isEqualTo(ServantOrder.FREE);
    assertThat(pulsed).isEmpty();
  }

  @Test
  void ownersWhoseServantsDoNotObeyTheOverseerAreIgnored() {
    MobRef servant = servant(at(3D, world));
    obeying.set(false);

    receiver.receive(signal(GolemSignal.FOLLOW, null, null));

    assertThat(scheduled).isEmpty();
    assertThat(orders.get(servant)).isEqualTo(ServantOrder.FREE);
  }

  @Test
  void theCrookChannelOnlyReachesServantsOnThatChannel() {
    MobRef servant = servant(at(3D, world));

    servantChannels.set(ChannelMask.of(DyeColor.BLUE));
    receiver.receive(channelSignal(DyeColor.RED));
    assertThat(orders.get(servant)).isEqualTo(ServantOrder.FREE);

    servantChannels.set(ChannelMask.NONE);
    receiver.receive(channelSignal(DyeColor.RED));
    assertThat(orders.get(servant)).isEqualTo(ServantOrder.FREE);

    servantChannels.set(ChannelMask.of(DyeColor.RED));
    receiver.receive(channelSignal(DyeColor.RED));
    assertThat(orders.get(servant)).isEqualTo(ServantOrder.hold(at(3D, world)));
  }

  @Test
  void deadServantsAreSkipped() {
    MobRef servant = servant(at(3D, world));
    lenient().when(servant.isDead()).thenReturn(true);

    receiver.receive(signal(GolemSignal.FOLLOW, null, null));

    assertThat(orders.get(servant)).isEqualTo(ServantOrder.FREE);
    verify(servant, never()).setTarget(any());
  }

  private OverseerSignal signal(GolemSignal signal, LivingRef target, DyeColor channel) {
    return new OverseerSignal(OWNER, signal, at(0D, world), 16D, channel, target, 25_000L, null, pulsed::add);
  }

  private OverseerSignal channelSignal(DyeColor channel) {
    return signal(GolemSignal.HOLD, null, channel);
  }

  private Loc at(double x, WorldRef in) {
    return new Loc(in, x, 64D, 0D);
  }

  private MobRef servant(Loc location) {
    MobRef servant = mock(MobRef.class);
    lenient().when(servant.isValid()).thenReturn(true);
    lenient().when(servant.location()).thenAnswer(call -> location);
    lenient().when(servant.world()).thenReturn(location.world());
    pack.add(servant);
    orders.put(servant, ServantOrder.FREE);
    return servant;
  }

  private final class FakePack implements ServantPack {
    @Override
    public List<MobRef> servants(UUID owner) {
      return OWNER.equals(owner) ? List.copyOf(pack) : List.of();
    }

    @Override
    public long expiresAt(MobRef servant) {
      return 0L;
    }

    @Override
    public ServantOrder order(MobRef servant) {
      return orders.getOrDefault(servant, ServantOrder.FREE);
    }

    @Override
    public boolean order(MobRef servant, ServantOrder order) {
      orders.put(servant, order);
      return true;
    }

    @Override
    public boolean surrender(MobRef servant) {
      return pack.remove(servant);
    }

    @Override
    public void crumble(MobRef servant) {
      pack.remove(servant);
    }
  }
}
