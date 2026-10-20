package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.api.golem.overseer.GolemSignal;
import art.arcane.adapt.api.golem.overseer.OverseerSignal;
import art.arcane.adapt.api.minion.ServantOrder;
import art.arcane.adapt.api.minion.ServantPack;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
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

  private final World world = mock(World.class);
  private final World elsewhere = mock(World.class);
  private final List<Skeleton> pack = new ArrayList<>();
  private final Map<Skeleton, ServantOrder> orders = new HashMap<>();
  private final List<Entity> pulsed = new ArrayList<>();
  private final List<Entity> scheduled = new ArrayList<>();
  private final AtomicBoolean obeying = new AtomicBoolean(true);
  private final AtomicInteger servantChannels = new AtomicInteger(ChannelMask.NONE);
  private final ServantPack servants = new FakePack();
  private final OverseerServants receiver = new OverseerServants(owner -> obeying.get(), owner -> servantChannels.get(), () -> Optional.of(servants));
  private MockedStatic<J> scheduling;

  @BeforeEach
  void setUp() {
    scheduling = mockStatic(J.class);
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class))).thenAnswer(call -> {
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
    Skeleton near = servant(at(10D, world));
    Skeleton far = servant(at(17D, world));
    Skeleton away = servant(at(1D, elsewhere));

    receiver.receive(signal(GolemSignal.FOLLOW, null, null));

    assertThat(orders.get(near)).isEqualTo(ServantOrder.follow());
    assertThat(orders.get(far)).isEqualTo(ServantOrder.FREE);
    assertThat(orders.get(away)).isEqualTo(ServantOrder.FREE);
    assertThat(pulsed).containsExactly(near);
  }

  @Test
  void holdAnchorsEachServantWhereItStandsAndDropsItsTarget() {
    Skeleton servant = servant(at(6D, world));

    receiver.receive(signal(GolemSignal.HOLD, null, null));

    assertThat(orders.get(servant)).isEqualTo(ServantOrder.hold(at(6D, world)));
    verify(servant).setTarget(null);
  }

  @Test
  void sicCommandsEveryServantInRangeAtTheTargetUntilTheHoldEnds() {
    LivingEntity target = mock(Zombie.class);
    Skeleton servant = servant(at(3D, world));
    orders.put(servant, ServantOrder.follow());

    receiver.receive(signal(GolemSignal.SIC, target, null));

    assertThat(orders.get(servant)).isEqualTo(ServantOrder.follow().command(target, 25_000L));
    verify(servant).setTarget(target);
    assertThat(pulsed).containsExactly(servant);
  }

  @Test
  void aMovementSignalEndsTheSicAndDropsItsTarget() {
    LivingEntity target = mock(Zombie.class);
    Skeleton servant = servant(at(3D, world));
    orders.put(servant, ServantOrder.FREE.command(target, 25_000L));
    lenient().when(servant.getTarget()).thenReturn(target);

    receiver.receive(signal(GolemSignal.FOLLOW, null, null));

    assertThat(orders.get(servant)).isEqualTo(ServantOrder.follow());
    verify(servant).setTarget(null);
  }

  @Test
  void homeAndWorkNeverReachServants() {
    Skeleton servant = servant(at(3D, world));

    receiver.receive(signal(GolemSignal.HOME, null, null));
    receiver.receive(signal(GolemSignal.WORK, null, null));

    assertThat(scheduled).isEmpty();
    assertThat(orders.get(servant)).isEqualTo(ServantOrder.FREE);
    assertThat(pulsed).isEmpty();
  }

  @Test
  void ownersWhoseServantsDoNotObeyTheOverseerAreIgnored() {
    Skeleton servant = servant(at(3D, world));
    obeying.set(false);

    receiver.receive(signal(GolemSignal.FOLLOW, null, null));

    assertThat(scheduled).isEmpty();
    assertThat(orders.get(servant)).isEqualTo(ServantOrder.FREE);
  }

  @Test
  void theCrookChannelOnlyReachesServantsOnThatChannel() {
    Skeleton servant = servant(at(3D, world));

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
    Skeleton servant = servant(at(3D, world));
    lenient().when(servant.isDead()).thenReturn(true);

    receiver.receive(signal(GolemSignal.FOLLOW, null, null));

    assertThat(orders.get(servant)).isEqualTo(ServantOrder.FREE);
    verify(servant, never()).setTarget(any());
  }

  private OverseerSignal signal(GolemSignal signal, LivingEntity target, DyeColor channel) {
    return new OverseerSignal(OWNER, signal, at(0D, world), 16D, channel, target, 25_000L, null, pulsed::add);
  }

  private OverseerSignal channelSignal(DyeColor channel) {
    return signal(GolemSignal.HOLD, null, channel);
  }

  private Location at(double x, World in) {
    return new Location(in, x, 64D, 0D);
  }

  private Skeleton servant(Location location) {
    Skeleton servant = mock(Skeleton.class);
    lenient().when(servant.isValid()).thenReturn(true);
    lenient().when(servant.getLocation()).thenAnswer(call -> location.clone());
    lenient().when(servant.getWorld()).thenReturn(location.getWorld());
    pack.add(servant);
    orders.put(servant, ServantOrder.FREE);
    return servant;
  }

  private final class FakePack implements ServantPack {
    @Override
    public List<Skeleton> servants(UUID owner) {
      return OWNER.equals(owner) ? List.copyOf(pack) : List.of();
    }

    @Override
    public long expiresAt(Skeleton servant) {
      return 0L;
    }

    @Override
    public ServantOrder order(Skeleton servant) {
      return orders.getOrDefault(servant, ServantOrder.FREE);
    }

    @Override
    public boolean order(Skeleton servant, ServantOrder order) {
      orders.put(servant, order);
      return true;
    }

    @Override
    public boolean surrender(Skeleton servant) {
      return pack.remove(servant);
    }

    @Override
    public void crumble(Skeleton servant) {
      pack.remove(servant);
    }
  }
}
