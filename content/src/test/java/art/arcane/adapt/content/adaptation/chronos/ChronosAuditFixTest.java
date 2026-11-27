package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.SchedulerForwarding;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChronosAuditFixTest {
  private static final Path INSTANT_RECALL_SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/chronos/ChronosInstantRecall.java"
  );

  @TempDir
  Path dataDirectory;

  private TestPlatform platform;
  private TestAdapt adapt;

  @BeforeEach
  void setUp() {
    platform = TestPlatform.builder(dataDirectory).build();
    adapt = TestAdapt.start(platform, List.of());
  }

  @AfterEach
  void tearDown() {
    adapt.close();
  }

  @Test
  void instantRecallRestoresTheCompletePlayerMovementState() {
    PlayerRef player = mock(PlayerRef.class);
    when(player.gameMode()).thenReturn(GameMode.SPECTATOR);

    assertThat(ChronosInstantRecall.restoreOwnedPlayerState(
        player,
        GameMode.ADVENTURE,
        true,
        true
    )).isTrue();

    InOrder order = inOrder(player);
    order.verify(player).setGameMode(GameMode.ADVENTURE);
    order.verify(player).setAllowFlight(true);
    order.verify(player).setFlying(true);
  }

  @Test
  void instantRecallDoesNotClobberAnExternallyChangedGameMode() {
    PlayerRef player = mock(PlayerRef.class);
    when(player.gameMode()).thenReturn(GameMode.CREATIVE);

    assertThat(ChronosInstantRecall.restoreOwnedPlayerState(
        player,
        GameMode.SURVIVAL,
        false,
        false
    )).isFalse();

    verify(player, times(0)).setGameMode(any(GameMode.class));
    verify(player, times(0)).setAllowFlight(false);
    verify(player, times(0)).setFlying(false);
  }

  @Test
  void instantRecallStampedRecoveryHealsOwnedSpectatorState() throws Exception {
    ChronosInstantRecall adaptation = TestContent.adaptation(Id.adapt("chronos"), ChronosInstantRecall::new);
    PlayerRef player = mock(PlayerRef.class);
    DataHolder data = mock(DataHolder.class);
    when(player.data()).thenReturn(data);
    when(player.gameMode()).thenReturn(GameMode.SPECTATOR);
    when(data.get(any())).thenReturn("SURVIVAL:0:0");

    restoreStampedRewindState(adaptation, player);

    verify(player).setSpectatorTarget(null);
    verify(player).setGameMode(GameMode.SURVIVAL);
    verify(player).setAllowFlight(false);
    verify(player).setFlying(false);
    verify(data).remove(any(DataKey.class));
  }

  @Test
  void instantRecallRejectsAConsumedClockCastWithoutAClock() {
    ChronosInstantRecall adaptation = TestContent.adaptation(Id.adapt("chronos"), ChronosInstantRecall::new);
    adaptation.getConfig().consumeClock = true;
    PlayerRef player = mock(PlayerRef.class);
    PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
    ItemRef mainHand = mock(ItemRef.class);
    ItemRef offHand = mock(ItemRef.class);
    when(player.inventory()).thenReturn(inventory);
    when(inventory.mainHand()).thenReturn(mainHand);
    when(inventory.offHand()).thenReturn(offHand);
    when(mainHand.type()).thenReturn(Id.minecraft("air"));
    when(offHand.type()).thenReturn(Id.minecraft("air"));
    when(mainHand.isEmpty()).thenReturn(true);
    when(offHand.isEmpty()).thenReturn(true);

    assertThat(adaptation.consumeRecallClock(player)).isFalse();
  }

  @Test
  void instantRecallCommitsFinalStateOnlyAfterItsTeleportSucceeds() {
    assertThat(ChronosInstantRecall.shouldCommitFinalRecall(
        true,
        null,
        true,
        true,
        false
    )).isTrue();
    assertThat(ChronosInstantRecall.shouldCommitFinalRecall(
        false,
        null,
        true,
        true,
        false
    )).isFalse();
    assertThat(ChronosInstantRecall.shouldCommitFinalRecall(
        true,
        new IllegalStateException("teleport failed"),
        true,
        true,
        false
    )).isFalse();
    assertThat(ChronosInstantRecall.shouldCommitFinalRecall(
        true,
        null,
        false,
        true,
        false
    )).isFalse();
    assertThat(ChronosInstantRecall.shouldCommitFinalRecall(
        true,
        null,
        true,
        true,
        true
    )).isFalse();
  }

  @Test
  void instantRecallUnregisterDrainsOwnedVisualCleanup() throws Exception {
    ChronosInstantRecall adaptation = TestContent.adaptation(Id.adapt("chronos"), ChronosInstantRecall::new);
    PlayerRef player = mock(PlayerRef.class);
    UUID playerId = UUID.randomUUID();
    AtomicBoolean cleaned = new AtomicBoolean(false);
    rewindCleanups(adaptation).put(
        playerId,
        new ChronosInstantRecall.RewindVisualState(player, () -> cleaned.set(true))
    );

    try (MockedStatic<Tasks> scheduling = SchedulerForwarding.mockTasks()) {
      scheduling.when(() -> Tasks.runEntity(same(player), any(Runnable.class))).thenAnswer(invocation -> {
        Runnable cleanup = invocation.getArgument(1);
        cleanup.run();
        return true;
      });

      adaptation.unregister();

      scheduling.verify(() -> Tasks.runEntity(same(player), any(Runnable.class)), times(1));
    }

    assertThat(cleaned).isTrue();
    assertThat(rewindCleanups(adaptation)).isEmpty();
  }

  @Test
  void instantRecallRuntimeActivationAcceptsRewindsAgain() throws Exception {
    ChronosInstantRecall adaptation = TestContent.adaptation(Id.adapt("chronos"), ChronosInstantRecall::new);
    AtomicBoolean accepting = acceptingRewinds(adaptation);
    accepting.set(false);

    adaptation.onRuntimeActivated();

    String source = Files.readString(INSTANT_RECALL_SOURCE);
    String activation = method(source, "protected void onRuntimeActivated()", "public void unregister()");
    assertThat(activation).contains("acceptingRewinds.set(true)");
    assertThat(accepting).isTrue();
  }

  @Test
  void temporalEchoRevalidatesTheCurrentActiveLevel() {
    ChronosTemporalEcho adaptation = mock(ChronosTemporalEcho.class, CALLS_REAL_METHODS);
    PlayerRef player = mock(PlayerRef.class);
    when(player.isOnline()).thenReturn(true);
    when(player.isDead()).thenReturn(false);
    doReturn(3, 0).when(adaptation).getActiveLevel(player);

    assertThat(adaptation.resolveDelayedEchoLevel(player)).isEqualTo(3);
    assertThat(adaptation.resolveDelayedEchoLevel(player)).isZero();

    when(player.isOnline()).thenReturn(false);
    assertThat(adaptation.resolveDelayedEchoLevel(player)).isZero();
  }

  @Test
  void timeBombQuitRestoresAndForgetsFrozenPlayerState() throws Exception {
    ChronosTimeBomb adaptation = TestContent.adaptation(Id.adapt("chronos"), ChronosTimeBomb::new);
    PlayerRef player = mock(PlayerRef.class);
    DataHolder data = mock(DataHolder.class);
    UUID playerId = UUID.randomUUID();
    when(player.uuid()).thenReturn(playerId);
    when(player.data()).thenReturn(data);
    when(player.allowFlight()).thenReturn(false);
    when(player.isFlying()).thenReturn(false);
    when(player.hasGravity()).thenReturn(true);
    when(player.isOnGround()).thenReturn(false);
    freezePlayer(adaptation, player);

    adaptation.on(new PlayerQuitEvent(player));

    InOrder order = inOrder(player);
    order.verify(player).setAllowFlight(true);
    order.verify(player).setFlying(true);
    order.verify(player).setGravity(false);
    order.verify(player).setGravity(true);
    order.verify(player).setFlying(false);
    order.verify(player).setAllowFlight(false);
    verify(data).remove(any(DataKey.class));
    assertThat(frozenPlayers(adaptation)).doesNotContainKey(playerId);
  }

  @Test
  void queuedTimeBombCleanupPreservesTheNewFreezeAndRecoveryStamp() throws Exception {
    ChronosTimeBomb adaptation = TestContent.adaptation(Id.adapt("chronos"), ChronosTimeBomb::new);
    LivingRef entity = mock(LivingRef.class);
    DataHolder data = mock(DataHolder.class);
    UUID entityId = UUID.randomUUID();
    when(entity.uuid()).thenReturn(entityId);
    when(entity.data()).thenReturn(data);
    when(entity.velocity()).thenReturn(new Vec3(0.4D, 0D, 0D));
    when(entity.hasGravity()).thenReturn(true);
    when(entity.hasAi()).thenReturn(true);
    freezeEntity(adaptation, entity);
    Map<UUID, Object> states = frozenEntities(adaptation);
    Object old = states.remove(entityId);
    when(entity.hasGravity()).thenReturn(false);
    when(entity.hasAi()).thenReturn(false);
    freezeEntity(adaptation, entity);
    Object current = states.get(entityId);
    Method release = ChronosTimeBomb.class.getDeclaredMethod("unfreezeEntity", EntityRef.class, old.getClass());
    release.setAccessible(true);
    clearInvocations(entity, data);
    release.invoke(adaptation, entity, old);
    verify(entity, never()).setGravity(true);
    verify(entity, never()).setAi(true);
    verify(data, never()).remove(any(DataKey.class));
    release.invoke(adaptation, entity, current);
    states.remove(entityId);
    verify(entity).setGravity(true);
    verify(entity).setAi(true);
    verify(data).remove(any(DataKey.class));
    assertThat(TemporalFreezeRegistry.isFrozen(entity)).isFalse();
  }

  @Test
  void timeBombUnregisterRestoresFrozenEntitiesOnTheirOwner() throws Exception {
    ChronosTimeBomb adaptation = TestContent.adaptation(Id.adapt("chronos"), ChronosTimeBomb::new);
    EntityRef entity = mock(EntityRef.class);
    DataHolder data = mock(DataHolder.class);
    UUID entityId = UUID.randomUUID();
    Vec3 initialVelocity = new Vec3(0.25D, 0.5D, -0.25D);
    when(entity.uuid()).thenReturn(entityId);
    when(entity.data()).thenReturn(data);
    when(entity.velocity()).thenReturn(initialVelocity);
    when(entity.hasGravity()).thenReturn(true);
    freezeEntity(adaptation, entity);
    platform.host().track(entity);

    try (MockedStatic<Tasks> scheduling = SchedulerForwarding.mockTasks()) {
      scheduling.when(() -> Tasks.runEntity(same(entity), any(Runnable.class))).thenAnswer(invocation -> {
        Runnable restoration = invocation.getArgument(1);
        restoration.run();
        return true;
      });

      adaptation.unregister();

      scheduling.verify(() -> Tasks.runEntity(same(entity), any(Runnable.class)), times(1));
    }

    verify(entity).setGravity(true);
    verify(entity).setVelocity(initialVelocity);
    verify(data).remove(any(DataKey.class));
    assertThat(frozenEntities(adaptation)).isEmpty();
  }

  private static void freezePlayer(ChronosTimeBomb adaptation, PlayerRef player) throws Exception {
    Method method = ChronosTimeBomb.class.getDeclaredMethod("freezePlayer", PlayerRef.class, UUID.class);
    method.setAccessible(true);
    method.invoke(adaptation, player, UUID.randomUUID());
  }

  private static void freezeEntity(ChronosTimeBomb adaptation, EntityRef entity) throws Exception {
    Method method = ChronosTimeBomb.class.getDeclaredMethod("freezeEntity", EntityRef.class, UUID.class);
    method.setAccessible(true);
    method.invoke(adaptation, entity, UUID.randomUUID());
  }

  private static void restoreStampedRewindState(
      ChronosInstantRecall adaptation,
      PlayerRef player
  ) throws Exception {
    Method method = ChronosInstantRecall.class.getDeclaredMethod(
        "restoreStampedRewindState",
        PlayerRef.class
    );
    method.setAccessible(true);
    method.invoke(adaptation, player);
  }

  private static AtomicBoolean acceptingRewinds(
      ChronosInstantRecall adaptation
  ) throws Exception {
    Field field = ChronosInstantRecall.class.getDeclaredField("acceptingRewinds");
    field.setAccessible(true);
    return (AtomicBoolean) field.get(adaptation);
  }

  @SuppressWarnings("unchecked")
  private static Map<UUID, ChronosInstantRecall.RewindVisualState> rewindCleanups(
      ChronosInstantRecall adaptation
  ) throws Exception {
    Field field = ChronosInstantRecall.class.getDeclaredField("rewindCleanups");
    field.setAccessible(true);
    return (Map<UUID, ChronosInstantRecall.RewindVisualState>) field.get(adaptation);
  }

  @SuppressWarnings("unchecked")
  private static Map<UUID, Object> frozenPlayers(ChronosTimeBomb adaptation) throws Exception {
    Field field = ChronosTimeBomb.class.getDeclaredField("frozenPlayers");
    field.setAccessible(true);
    return (Map<UUID, Object>) field.get(adaptation);
  }

  @SuppressWarnings("unchecked")
  private static Map<UUID, Object> frozenEntities(ChronosTimeBomb adaptation) throws Exception {
    Field field = ChronosTimeBomb.class.getDeclaredField("frozenEntities");
    field.setAccessible(true);
    return (Map<UUID, Object>) field.get(adaptation);
  }

  private static String method(String source, String startMarker, String endMarker) {
    int start = source.indexOf(startMarker);
    int end = source.indexOf(endMarker, start);
    if (start < 0 || end < 0) {
      throw new IllegalArgumentException(
          "Missing method markers: " + startMarker + ", " + endMarker
      );
    }
    return source.substring(start, end);
  }
}
