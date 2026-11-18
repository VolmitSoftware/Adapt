package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.content.integration.iris.IrisTreeFellerPort;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AxeIrisFellerTest {
  private IrisTreeFellerPort iris;

  @BeforeEach
  void installIris() {
    iris = mock(IrisTreeFellerPort.class);
    AdaptPorts.install(IrisTreeFellerPort.class, iris);
  }

  @AfterEach
  void uninstallIris() {
    AdaptPorts.uninstall(IrisTreeFellerPort.class);
  }

  @Test
  void durabilityPreservationUsesTheExactThreeLevelProgression() {
    assertThat(AxeIrisFeller.durabilityPreservationChance(1)).isZero();
    assertThat(AxeIrisFeller.durabilityPreservationChance(2)).isEqualTo(25);
    assertThat(AxeIrisFeller.durabilityPreservationChance(3)).isEqualTo(75);
  }

  @Test
  void levelsOutsideTheConfiguredRangeClampToTheNearestProgressionValue() {
    assertThat(AxeIrisFeller.durabilityPreservationChance(0)).isZero();
    assertThat(AxeIrisFeller.durabilityPreservationChance(4)).isEqualTo(75);
  }

  @Test
  void configNormalizationPreservesTheConfiguredLevelCap() {
    AxeIrisFeller adaptation = new AxeIrisFeller();
    AxeIrisFeller.Config config = new AxeIrisFeller.Config();
    config.maxLevel = 9;
    config.hungerCost = -4;
    config.cooldownSeconds = -30;

    adaptation.normalizeLoadedConfig(config);

    assertThat(config.maxLevel).isEqualTo(9);
    assertThat(config.hungerCost).isZero();
    assertThat(config.cooldownSeconds).isZero();
    assertThat(adaptation.shouldCanonicalizeConfigOnLoad()).isTrue();
  }

  @Test
  void runCostsDefaultToOneShankPerLogAndThirtySecondActivationCooldown() {
    AxeIrisFeller.Config config = new AxeIrisFeller.Config();

    assertThat(config.hungerCost).isEqualTo(2);
    assertThat(config.cooldownSeconds).isEqualTo(30);
    assertThat(AxeIrisFeller.cooldownMillis(config.cooldownSeconds)).isEqualTo(30000L);
    assertThat(AxeIrisFeller.cooldownMillis(-1)).isZero();
  }

  @Test
  void acceptedRunChargesOnlyCommittedLogsAndFailedReservationsNeverMutateHunger() {
    TestAxeIrisFeller adaptation = new TestAxeIrisFeller();
    RuntimeFixture fixture = runtimeFixture(20);
    AtomicReference<IrisTreeFellerPort.RunHooks> runHooks = new AtomicReference<>();

    stubRecognizedTree(iris, fixture);
    when(iris.tryFell(
        same(fixture.event()),
        eq(0),
        any(IrisTreeFellerPort.RunHooks.class)
    )).thenAnswer(invocation -> {
      runHooks.set(invocation.getArgument(2));
      return true;
    });

    adaptation.on(fixture.event());
    verify(fixture.player(), never()).setFoodLevel(anyInt());
    assertThat(runHooks.get()).isNotNull();

    adaptation.on(fixture.event());

    runHooks.get().onActivationAccepted();

    adaptation.on(fixture.event());

    verify(iris, times(2)).tryFell(
        same(fixture.event()),
        eq(0),
        any(IrisTreeFellerPort.RunHooks.class)
    );

    assertThat(runHooks.get().reserveLogCost()).isTrue();
    assertThat(fixture.foodLevel()).hasValue(20);
    runHooks.get().commitLogCost();
    assertThat(fixture.foodLevel()).hasValue(18);

    assertThat(runHooks.get().reserveLogCost()).isTrue();
    assertThat(fixture.foodLevel()).hasValue(18);
    runHooks.get().refundLogCost();
    assertThat(fixture.foodLevel()).hasValue(18);
  }

  @Test
  void declinedFellCommitsNeitherHungerNorCooldown() {
    TestAxeIrisFeller adaptation = new TestAxeIrisFeller();
    RuntimeFixture fixture = runtimeFixture(20);

    stubRecognizedTree(iris, fixture);
    when(iris.tryFell(
        same(fixture.event()),
        eq(0),
        any(IrisTreeFellerPort.RunHooks.class)
    )).thenReturn(false);

    adaptation.on(fixture.event());
    adaptation.on(fixture.event());

    verify(iris, times(2)).tryFell(
        same(fixture.event()),
        eq(0),
        any(IrisTreeFellerPort.RunHooks.class)
    );
    verify(fixture.player(), never()).setFoodLevel(anyInt());
  }

  @Test
  void cancelledAndNonTreeBreaksCommitNoCost() {
    TestAxeIrisFeller adaptation = new TestAxeIrisFeller();
    RuntimeFixture cancelled = runtimeFixture(20);
    RuntimeFixture nonTree = runtimeFixture(20);
    when(cancelled.event().isCancelled()).thenReturn(true);

    when(iris.isManagedBreak(nonTree.event())).thenReturn(false);
    when(iris.isTreeBlock(nonTree.block())).thenReturn(false);

    adaptation.on(cancelled.event());
    adaptation.on(nonTree.event());

    verify(iris, never()).tryFell(
        same(cancelled.event()),
        eq(0),
        any(IrisTreeFellerPort.RunHooks.class)
    );
    verify(iris, never()).tryFell(
        same(nonTree.event()),
        eq(0),
        any(IrisTreeFellerPort.RunHooks.class)
    );
    verify(cancelled.player(), never()).setFoodLevel(anyInt());
    verify(nonTree.player(), never()).setFoodLevel(anyInt());
  }

  @Test
  void insufficientHungerDoesNotAskIrisOrStartCooldown() {
    TestAxeIrisFeller adaptation = new TestAxeIrisFeller();
    RuntimeFixture fixture = runtimeFixture(1);

    stubRecognizedTree(iris, fixture);

    adaptation.on(fixture.event());
    adaptation.on(fixture.event());

    verify(iris, never()).tryFell(
        same(fixture.event()),
        eq(0),
        any(IrisTreeFellerPort.RunHooks.class)
    );
    verify(fixture.player(), never()).setFoodLevel(anyInt());
  }

  @Test
  void runHaltsWhenHungerCannotReserveTheNextLog() {
    TestAxeIrisFeller adaptation = new TestAxeIrisFeller();
    RuntimeFixture fixture = runtimeFixture(2);
    AtomicReference<IrisTreeFellerPort.RunHooks> runHooks = new AtomicReference<>();

    stubRecognizedTree(iris, fixture);
    when(iris.tryFell(
        same(fixture.event()),
        eq(0),
        any(IrisTreeFellerPort.RunHooks.class)
    )).thenAnswer(invocation -> {
      runHooks.set(invocation.getArgument(2));
      return true;
    });

    adaptation.on(fixture.event());

    assertThat(runHooks.get()).isNotNull();
    runHooks.get().onActivationAccepted();
    assertThat(runHooks.get().reserveLogCost()).isTrue();
    assertThat(fixture.foodLevel()).hasValue(2);
    runHooks.get().commitLogCost();
    assertThat(fixture.foodLevel()).hasValue(0);
    assertThat(runHooks.get().reserveLogCost()).isFalse();
    assertThat(fixture.foodLevel()).hasValue(0);
  }

  @Test
  void activationRequiresSneaking() {
    TestAxeIrisFeller adaptation = new TestAxeIrisFeller();
    RuntimeFixture fixture = runtimeFixture(20);
    when(fixture.player().isSneaking()).thenReturn(false);

    when(iris.isManagedBreak(fixture.event())).thenReturn(false);

    adaptation.on(fixture.event());

    verify(iris, never()).tryFell(
        same(fixture.event()),
        eq(0),
        any(IrisTreeFellerPort.RunHooks.class)
    );
  }

  @Test
  void irisClaimsBeforeWoodVeinminerAtHighPriority() throws NoSuchMethodException {
    Method claim = AxeIrisFeller.class.getDeclaredMethod("on", BlockBreakEvent.class);
    Handle claimHandler = claim.getAnnotation(Handle.class);

    assertThat(claimHandler).isNotNull();
    assertThat(claimHandler.priority()).isEqualTo(EventPriority.HIGH);
    assertThat(claimHandler.ignoreCancelled()).isTrue();
  }

  @Test
  void personalReserveRejectsWholeLogWithoutDiscountingAcceptedCost() {
    TestAxeIrisFeller adaptation = new TestAxeIrisFeller();
    adaptation.reserve = AxePreferences.Reserve.FIVE;
    RuntimeFixture fixture = runtimeFixture(8);
    AtomicReference<IrisTreeFellerPort.RunHooks> hooks = new AtomicReference<>();
    stubRecognizedTree(iris, fixture);
    when(iris.tryFell(same(fixture.event()), eq(0), any(IrisTreeFellerPort.RunHooks.class)))
        .thenAnswer(invocation -> {
          hooks.set(invocation.getArgument(2));
          hooks.get().onActivationAccepted();
          return true;
        });
    adaptation.on(fixture.event());
    assertThat(hooks.get().reserveLogCost()).isTrue();
    assertThat(fixture.foodLevel()).hasValue(8);
    hooks.get().commitLogCost();
    assertThat(fixture.foodLevel()).hasValue(6);
    assertThat(hooks.get().reserveLogCost()).isFalse();
    assertThat(fixture.foodLevel()).hasValue(6);
  }

  @Test
  void latchedRunStopsOnNextSneakWithoutRefundingCommittedFood() {
    TestAxeIrisFeller adaptation = new TestAxeIrisFeller();
    adaptation.latched = true;
    RuntimeFixture fixture = runtimeFixture(20);
    AtomicReference<IrisTreeFellerPort.RunHooks> hooks = new AtomicReference<>();
    stubRecognizedTree(iris, fixture);
    when(iris.tryFell(same(fixture.event()), eq(0), any(IrisTreeFellerPort.RunHooks.class)))
        .thenAnswer(invocation -> {
          hooks.set(invocation.getArgument(2));
          hooks.get().onActivationAccepted();
          return true;
        });
    adaptation.on(fixture.event());
    assertThat(hooks.get().requiresSneaking()).isFalse();
    assertThat(hooks.get().reserveLogCost()).isTrue();
    hooks.get().commitLogCost();
    adaptation.on(new PlayerToggleSneakEvent(fixture.player(), true));
    assertThat(hooks.get().reserveLogCost()).isFalse();
    assertThat(fixture.foodLevel()).hasValue(18);
  }

  private static void stubRecognizedTree(IrisTreeFellerPort iris, RuntimeFixture fixture) {
    when(iris.isManagedBreak(fixture.event())).thenReturn(false);
    when(iris.isTreeBlock(fixture.block())).thenReturn(true);
  }

  private static RuntimeFixture runtimeFixture(int foodLevel) {
    BlockBreakEvent event = mock(BlockBreakEvent.class);
    BlockRef block = mock(BlockRef.class);
    WorldRef world = mock(WorldRef.class);
    when(world.uid()).thenReturn(UUID.randomUUID());
    Loc location = new Loc(world, 0, 64, 0);
    PlayerRef player = mock(PlayerRef.class);
    PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
    ItemRef axe = mock(ItemRef.class);

    when(event.block()).thenReturn(block);
    when(block.world()).thenReturn(world);
    when(event.player()).thenReturn(player);
    when(block.location()).thenReturn(location);
    when(player.uuid()).thenReturn(UUID.randomUUID());
    when(player.inventory()).thenReturn(inventory);
    when(player.isSneaking()).thenReturn(true);
    AtomicInteger currentFoodLevel = new AtomicInteger(foodLevel);
    when(player.foodLevel()).thenAnswer(invocation -> currentFoodLevel.get());
    doAnswer(invocation -> {
      currentFoodLevel.set(invocation.getArgument(0));
      return null;
    }).when(player).setFoodLevel(anyInt());
    when(inventory.mainHand()).thenReturn(axe);
    when(axe.type()).thenReturn(Id.minecraft("iron_axe"));
    return new RuntimeFixture(event, block, player, currentFoodLevel);
  }

  private static final class TestAxeIrisFeller extends AxeIrisFeller {
    private final Config config = new Config();
    private AxePreferences.Reserve reserve = AxePreferences.Reserve.NONE;
    private boolean latched;

    @Override
    public <E extends Enum<E>> E preference(PlayerRef player, PlayerPreference<E> preference) {
      if (preference == AxePreferences.RESERVE) { return preference.parse(reserve.name()); }
      if (preference == AxePreferences.LATCH) { return preference.parse(latched ? "ON" : "OFF"); }
      return preference.defaultValue();
    }

    @Override
    public int getActiveLevel(PlayerRef player) {
      return 1;
    }

    @Override
    public Config getConfig() {
      return config;
    }

    @Override
    public Adaptation.BlockActionContext resolveBlockBreakContext(PlayerRef player, Loc location,
                                                            Predicate<PlayerRef> requirement,
                                                            boolean survivalOnly) {
      return new Adaptation.BlockActionContext(player, location, 1);
    }
  }

  private record RuntimeFixture(BlockBreakEvent event, BlockRef block, PlayerRef player,
                                AtomicInteger foodLevel) {
  }
}
