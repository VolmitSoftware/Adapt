package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.content.integration.iris.IrisTreeFellerLink;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
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
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AxeIrisFellerTest {
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
  void configNormalizationKeepsExactlyThreeLevels() {
    AxeIrisFeller adaptation = new AxeIrisFeller();
    AxeIrisFeller.Config config = new AxeIrisFeller.Config();
    config.maxLevel = 9;
    config.hungerCost = -4;
    config.cooldownSeconds = -30;

    adaptation.normalizeLoadedConfig(config);

    assertThat(config.maxLevel).isEqualTo(3);
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
    AtomicReference<IrisTreeFellerLink.RunHooks> runHooks = new AtomicReference<>();

    try (MockedStatic<IrisTreeFellerLink> iris = mockStatic(IrisTreeFellerLink.class)) {
      stubRecognizedTree(iris, fixture);
      iris.when(() -> IrisTreeFellerLink.tryFell(
          same(fixture.event()),
          eq(0),
          any(IrisTreeFellerLink.RunHooks.class)
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

      iris.verify(() -> IrisTreeFellerLink.tryFell(
          same(fixture.event()),
          eq(0),
          any(IrisTreeFellerLink.RunHooks.class)
      ), times(2));

      assertThat(runHooks.get().reserveLogCost()).isTrue();
      assertThat(fixture.foodLevel()).hasValue(20);
      runHooks.get().commitLogCost();
      assertThat(fixture.foodLevel()).hasValue(18);

      assertThat(runHooks.get().reserveLogCost()).isTrue();
      assertThat(fixture.foodLevel()).hasValue(18);
      runHooks.get().refundLogCost();
      assertThat(fixture.foodLevel()).hasValue(18);
    }
  }

  @Test
  void declinedFellCommitsNeitherHungerNorCooldown() {
    TestAxeIrisFeller adaptation = new TestAxeIrisFeller();
    RuntimeFixture fixture = runtimeFixture(20);

    try (MockedStatic<IrisTreeFellerLink> iris = mockStatic(IrisTreeFellerLink.class)) {
      stubRecognizedTree(iris, fixture);
      iris.when(() -> IrisTreeFellerLink.tryFell(
          same(fixture.event()),
          eq(0),
          any(IrisTreeFellerLink.RunHooks.class)
      )).thenReturn(false);

      adaptation.on(fixture.event());
      adaptation.on(fixture.event());

      iris.verify(() -> IrisTreeFellerLink.tryFell(
          same(fixture.event()),
          eq(0),
          any(IrisTreeFellerLink.RunHooks.class)
      ), times(2));
      verify(fixture.player(), never()).setFoodLevel(anyInt());
    }
  }

  @Test
  void cancelledAndNonTreeBreaksCommitNoCost() {
    TestAxeIrisFeller adaptation = new TestAxeIrisFeller();
    RuntimeFixture cancelled = runtimeFixture(20);
    RuntimeFixture nonTree = runtimeFixture(20);
    when(cancelled.event().isCancelled()).thenReturn(true);

    try (MockedStatic<IrisTreeFellerLink> iris = mockStatic(IrisTreeFellerLink.class)) {
      iris.when(() -> IrisTreeFellerLink.isManagedBreak(nonTree.event())).thenReturn(false);
      iris.when(() -> IrisTreeFellerLink.isTreeBlock(nonTree.block())).thenReturn(false);

      adaptation.on(cancelled.event());
      adaptation.on(nonTree.event());

      iris.verify(() -> IrisTreeFellerLink.tryFell(
          same(cancelled.event()),
          eq(0),
          any(IrisTreeFellerLink.RunHooks.class)
      ), never());
      iris.verify(() -> IrisTreeFellerLink.tryFell(
          same(nonTree.event()),
          eq(0),
          any(IrisTreeFellerLink.RunHooks.class)
      ), never());
      verify(cancelled.player(), never()).setFoodLevel(anyInt());
      verify(nonTree.player(), never()).setFoodLevel(anyInt());
    }
  }

  @Test
  void insufficientHungerDoesNotAskIrisOrStartCooldown() {
    TestAxeIrisFeller adaptation = new TestAxeIrisFeller();
    RuntimeFixture fixture = runtimeFixture(1);

    try (MockedStatic<IrisTreeFellerLink> iris = mockStatic(IrisTreeFellerLink.class)) {
      stubRecognizedTree(iris, fixture);

      adaptation.on(fixture.event());
      adaptation.on(fixture.event());

      iris.verify(() -> IrisTreeFellerLink.tryFell(
          same(fixture.event()),
          eq(0),
          any(IrisTreeFellerLink.RunHooks.class)
      ), never());
      verify(fixture.player(), never()).setFoodLevel(anyInt());
    }
  }

  @Test
  void runHaltsWhenHungerCannotReserveTheNextLog() {
    TestAxeIrisFeller adaptation = new TestAxeIrisFeller();
    RuntimeFixture fixture = runtimeFixture(2);
    AtomicReference<IrisTreeFellerLink.RunHooks> runHooks = new AtomicReference<>();

    try (MockedStatic<IrisTreeFellerLink> iris = mockStatic(IrisTreeFellerLink.class)) {
      stubRecognizedTree(iris, fixture);
      iris.when(() -> IrisTreeFellerLink.tryFell(
          same(fixture.event()),
          eq(0),
          any(IrisTreeFellerLink.RunHooks.class)
      )).thenAnswer(invocation -> {
        runHooks.set(invocation.getArgument(2));
        return true;
      });

      adaptation.on(fixture.event());

      assertThat(runHooks.get()).isNotNull();
      assertThat(runHooks.get().reserveLogCost()).isTrue();
      assertThat(fixture.foodLevel()).hasValue(2);
      runHooks.get().commitLogCost();
      assertThat(fixture.foodLevel()).hasValue(0);
      assertThat(runHooks.get().reserveLogCost()).isFalse();
      assertThat(fixture.foodLevel()).hasValue(0);
    }
  }

  @Test
  void activationRequiresSneaking() {
    TestAxeIrisFeller adaptation = new TestAxeIrisFeller();
    RuntimeFixture fixture = runtimeFixture(20);
    when(fixture.player().isSneaking()).thenReturn(false);

    try (MockedStatic<IrisTreeFellerLink> iris = mockStatic(IrisTreeFellerLink.class)) {
      iris.when(() -> IrisTreeFellerLink.isManagedBreak(fixture.event())).thenReturn(false);

      adaptation.on(fixture.event());

      iris.verify(() -> IrisTreeFellerLink.tryFell(
          same(fixture.event()),
          eq(0),
          any(IrisTreeFellerLink.RunHooks.class)
      ), never());
    }
  }

  @Test
  void irisClaimsBeforeWoodVeinminerAtHighPriority() throws NoSuchMethodException {
    Method claim = AxeIrisFeller.class.getDeclaredMethod("on", BlockBreakEvent.class);
    EventHandler claimHandler = claim.getAnnotation(EventHandler.class);

    assertThat(claimHandler).isNotNull();
    assertThat(claimHandler.priority()).isEqualTo(EventPriority.HIGH);
    assertThat(claimHandler.ignoreCancelled()).isTrue();
  }

  @Test
  void irisManagedBreaksAndSyntheticProtectionProbesCannotReachAdaptVeinminers() throws IOException {
    String irisFeller = Files.readString(Path.of(
        "src/main/java/art/arcane/adapt/content/adaptation/axe/AxeIrisFeller.java"));
    String woodVeinminer = Files.readString(Path.of(
        "src/main/java/art/arcane/adapt/content/adaptation/axe/AxeWoodVeinminer.java"));
    String leafVeinminer = Files.readString(Path.of(
        "src/main/java/art/arcane/adapt/content/adaptation/axe/AxeLeafVeinminer.java"));

    assertThat(irisFeller).contains("IrisTreeFellerLink.isManagedBreak(event)");
    assertThat(woodVeinminer).contains("IrisTreeFellerLink.isManagedBreak(e)");
    assertThat(leafVeinminer).contains("IrisTreeFellerLink.isManagedBreak(e)");
  }

  @Test
  void irisRecognitionPrecedesAdaptUsageAndProtectionGates() throws IOException {
    String source = Files.readString(Path.of(
        "src/main/java/art/arcane/adapt/content/adaptation/axe/AxeIrisFeller.java"));
    int treeRecognition = source.indexOf("IrisTreeFellerLink.isTreeBlock(block)");
    int adaptGates = source.indexOf("resolveBlockBreakContext(player, block.getLocation(), null, true)");

    assertThat(treeRecognition).isGreaterThanOrEqualTo(0);
    assertThat(adaptGates).isGreaterThan(treeRecognition);
  }

  @Test
  void adaptNeverWritesItsOwnVeinMarkerForAnIrisClaim() throws IOException {
    String source = Files.readString(Path.of(
        "src/main/java/art/arcane/adapt/content/adaptation/axe/AxeIrisFeller.java"));

    assertThat(source).doesNotContain("VEIN_MINED.add", "VEIN_MINED.remove");
  }

  private static void stubRecognizedTree(MockedStatic<IrisTreeFellerLink> iris, RuntimeFixture fixture) {
    iris.when(() -> IrisTreeFellerLink.isManagedBreak(fixture.event())).thenReturn(false);
    iris.when(() -> IrisTreeFellerLink.isTreeBlock(fixture.block())).thenReturn(true);
  }

  private static RuntimeFixture runtimeFixture(int foodLevel) {
    BlockBreakEvent event = mock(BlockBreakEvent.class);
    Block block = mock(Block.class);
    Location location = mock(Location.class);
    Player player = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    ItemStack axe = mock(ItemStack.class);

    when(event.getBlock()).thenReturn(block);
    when(event.getPlayer()).thenReturn(player);
    when(block.getLocation()).thenReturn(location);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    when(player.getInventory()).thenReturn(inventory);
    when(player.isSneaking()).thenReturn(true);
    AtomicInteger currentFoodLevel = new AtomicInteger(foodLevel);
    when(player.getFoodLevel()).thenAnswer(invocation -> currentFoodLevel.get());
    doAnswer(invocation -> {
      currentFoodLevel.set(invocation.getArgument(0));
      return null;
    }).when(player).setFoodLevel(anyInt());
    when(inventory.getItemInMainHand()).thenReturn(axe);
    when(axe.getType()).thenReturn(Material.IRON_AXE);
    return new RuntimeFixture(event, block, player, currentFoodLevel);
  }

  private static final class TestAxeIrisFeller extends AxeIrisFeller {
    private final Config config = new Config();

    @Override
    public Config getConfig() {
      return config;
    }

    @Override
    public Adaptation.BlockActionContext resolveBlockBreakContext(Player player, Location location,
                                                                  Predicate<Player> requirement,
                                                                  boolean survivalOnly) {
      return new Adaptation.BlockActionContext(player, location, 1);
    }
  }

  private record RuntimeFixture(BlockBreakEvent event, Block block, Player player,
                                AtomicInteger foodLevel) {
  }
}
