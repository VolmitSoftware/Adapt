package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.EngineTestBase;
import art.arcane.adapt.SchedulerForwarding;
import art.arcane.adapt.engine.attribute.AdaptAttributeService;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.content.golem.GolemRegistry;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.engine.minion.MinionBurden;
import art.arcane.adapt.engine.minion.MinionBurdens;
import art.arcane.adapt.content.minion.ServantOrder;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.testkit.MemoryData;
import art.arcane.adapt.util.common.scheduling.Tasks;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TragoulSkeletalServantBindingTest extends EngineTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000005a1");
  private static final DataKey<String> SERVANT_KEY = DataKey.adapt("tragoul_servant_owner", DataCodecs.STRING);
  private static final long LIFETIME_MILLIS = 60_000L;
  private static final int LIFETIME_TICKS = 1_200;

  private final List<Runnable> timers = new ArrayList<>();
  private final List<Runnable> immediate = new ArrayList<>();
  private final WorldRef world = mock(WorldRef.class);
  private final PlayerRef owner = mock(PlayerRef.class);
  private MockedStatic<Tasks> scheduling;
  private MockedStatic<AdaptAttributeService> attributes;
  private MinionBurden burden;
  private TragoulSkeletalServant adaptation;
  private TragoulSkeletalServant original;

  @BeforeEach
  void construct() throws ReflectiveOperationException {
    lenient().when(core.getPlatform().host().registries()).thenReturn(mock(RegistryLookup.class));
    burden = MinionBurdens.install();
    scheduling = SchedulerForwarding.mockTasks();
    scheduling.when(() -> Tasks.runEntity(any(EntityRef.class), any(Runnable.class), anyInt())).thenAnswer(invocation -> timers.add(invocation.getArgument(1)));
    scheduling.when(() -> Tasks.runEntity(any(EntityRef.class), any(Runnable.class))).thenAnswer(invocation -> immediate.add(invocation.getArgument(1)));
    attributes = mockStatic(AdaptAttributeService.class);
    attributes.when(AdaptAttributeService::get).thenReturn(mock(AdaptAttributeService.class));
    lenient().when(owner.uuid()).thenReturn(OWNER);
    original = TestContent.adaptation(Id.adapt("tragoul"), TragoulSkeletalServant::new);
    adaptation = spy(original);
    lenient().doNothing().when(adaptation).crumbleFx(any(MobRef.class));
  }

  @AfterEach
  void close() throws ReflectiveOperationException {
    attributes.close();
    scheduling.close();
    MinionBurdens.uninstall();
  }

  @Test
  void aSurrenderedServantLeavesTimedTrackingFreesItsCapSlotAndDropsItsSummonCost() {
    MobRef servant = servant(OWNER);
    adaptation.track(owner, servant, System.currentTimeMillis() + LIFETIME_MILLIS, LIFETIME_TICKS);
    assertThat(adaptation.servants(OWNER)).containsExactly(servant);
    assertThat(adaptation.expiresAt(servant)).isPositive();
    assertThat(burden.activeCount(OWNER)).isEqualTo(1);

    assertThat(adaptation.surrender(servant)).isTrue();

    assertThat(adaptation.servants(OWNER)).isEmpty();
    assertThat(adaptation.expiresAt(servant)).isZero();
    assertThat(adaptation.order(servant)).isEqualTo(ServantOrder.FREE);
    assertThat(TragoulSkeletalServant.isServant(servant)).isFalse();
    assertThat(burden.activeCount(OWNER)).isZero();
    assertThat(adaptation.surrender(servant)).isFalse();
  }

  @Test
  void aSurrenderedServantOutlivesItsTimerWhileATrackedOneCrumblesWhenItsTimeRunsOut() {
    MobRef kept = servant(OWNER);
    MobRef expiring = servant(OWNER);
    adaptation.track(owner, kept, System.currentTimeMillis() + LIFETIME_MILLIS, LIFETIME_TICKS);
    adaptation.track(owner, expiring, System.currentTimeMillis() + LIFETIME_MILLIS, LIFETIME_TICKS);
    adaptation.surrender(kept);

    for (Runnable timer : List.copyOf(timers)) {
      timer.run();
    }

    verify(kept, never()).remove();
    verify(expiring).remove();
  }

  @Test
  void anOwnersLogoutDismissesTrackedServantsButNotASurrenderedOne() {
    MobRef kept = servant(OWNER);
    MobRef tracked = servant(OWNER);
    adaptation.track(owner, kept, System.currentTimeMillis() + LIFETIME_MILLIS, LIFETIME_TICKS);
    adaptation.track(owner, tracked, System.currentTimeMillis() + LIFETIME_MILLIS, LIFETIME_TICKS);
    adaptation.surrender(kept);

    adaptation.on(new PlayerQuitEvent(owner));
    for (Runnable task : List.copyOf(immediate)) {
      task.run();
    }

    verify(kept, never()).remove();
    verify(tracked).remove();
  }

  @Test
  void onlyASkeletalServantCanBeSurrendered() {
    MobRef wild = skeleton(Map.of());

    assertThat(adaptation.surrender(wild)).isFalse();
  }

  @Test
  void unlearningDismissesTheLivePackAndRevokesBoundServants() {
    MobRef servant = servant(OWNER);
    adaptation.track(owner, servant, System.currentTimeMillis() + LIFETIME_MILLIS, LIFETIME_TICKS);
    GolemRegistry golems = mock(GolemRegistry.class);
    try (MockedStatic<GolemancyRuntime> runtime = mockStatic(GolemancyRuntime.class)) {
      runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(golems));

      adaptation.onLevelChanged(owner, 0);
      for (Runnable task : List.copyOf(immediate)) {
        task.run();
      }
    }

    verify(servant).remove();
    verify(golems).revokeBones(OWNER);
    assertThat(adaptation.servants(OWNER)).isEmpty();
    assertThat(burden.activeCount(OWNER)).isZero();
  }

  @Test
  void loweringALearnedServantLevelKeepsThePackAndBoundServants() {
    MobRef servant = servant(OWNER);
    adaptation.track(owner, servant, System.currentTimeMillis() + LIFETIME_MILLIS, LIFETIME_TICKS);
    try (MockedStatic<GolemancyRuntime> runtime = mockStatic(GolemancyRuntime.class)) {
      adaptation.onLevelChanged(owner, 1);
      runtime.verifyNoInteractions();
    }

    assertThat(adaptation.servants(OWNER)).containsExactly(servant);
    assertThat(burden.activeCount(OWNER)).isEqualTo(1);
    verify(servant, never()).remove();
  }
  @Test
  void crumblingRemovesTheBodyWithTheServantRemovalEffect() {
    MobRef bound = skeleton(Map.of());

    adaptation.crumble(bound);

    verify(adaptation).crumbleFx(bound);
    verify(bound).remove();
  }

  @Test
  void loadingTheConfigurationSetsTheHealthCostEveryLivingServantCarries() throws ReflectiveOperationException {
    assertThat(ComponentHost.of(original).reloadConfigSnapshot("healthCostPerMinion = 3.5\n", dataFolder.toPath().resolve("cost.toml").toFile(), false)).isTrue();
    assertThat(MinionBurdens.healthPerMinion(burden)).isEqualTo(3.5D);

    assertThat(ComponentHost.of(original).reloadConfigSnapshot("healthCostEnabled = false\n", dataFolder.toPath().resolve("off.toml").toFile(), false)).isTrue();
    assertThat(MinionBurdens.healthPerMinion(burden)).isZero();
  }

  private MobRef servant(UUID ownerId) {
    Map<DataKey<String>, String> values = new HashMap<>();
    values.put(SERVANT_KEY, ownerId.toString());
    return skeleton(values);
  }

  private MobRef skeleton(Map<DataKey<String>, String> initial) {
    MobRef skeleton = mock(MobRef.class);
    MemoryData data = new MemoryData();
    initial.forEach(data::set);
    lenient().when(skeleton.data()).thenReturn(data);
    lenient().when(skeleton.type()).thenReturn(Id.minecraft("skeleton"));
    lenient().when(skeleton.uuid()).thenReturn(UUID.randomUUID());
    lenient().when(skeleton.isValid()).thenReturn(true);
    lenient().when(skeleton.location()).thenReturn(new Loc(world, 0D, 64D, 0D));
    return skeleton;
  }
}
