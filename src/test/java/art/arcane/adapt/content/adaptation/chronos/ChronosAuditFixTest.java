package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.content.item.ChronoTimeBombItem;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChronosAuditFixTest extends AdaptTestBase {
  private static final Path INSTANT_RECALL_SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/chronos/ChronosInstantRecall.java"
  );
  private static final Path TEMPORAL_ECHO_SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/chronos/ChronosTemporalEcho.java"
  );

  @BeforeEach
  void configurePluginName() {
    lenient().when(plugin.getName()).thenReturn("Adapt");
    lenient().when(plugin.namespace()).thenReturn("adapt");
  }

  @Test
  void instantRecallRestoresTheCompletePlayerMovementState() {
    Player player = mock(Player.class);
    when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);

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
    Player player = mock(Player.class);
    when(player.getGameMode()).thenReturn(GameMode.CREATIVE);

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
    ChronosInstantRecall adaptation = new ChronosInstantRecall();
    Player player = mock(Player.class);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    when(player.getPersistentDataContainer()).thenReturn(data);
    when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
    when(data.get(any(NamespacedKey.class), same(PersistentDataType.STRING)))
        .thenReturn("SURVIVAL:0:0");

    restoreStampedRewindState(adaptation, player);

    verify(player).setSpectatorTarget(null);
    verify(player).setGameMode(GameMode.SURVIVAL);
    verify(player).setAllowFlight(false);
    verify(player).setFlying(false);
    verify(data).remove(any(NamespacedKey.class));
  }

  @Test
  void instantRecallRejectsAConsumedClockCastWithoutAClock() {
    ChronosInstantRecall adaptation = new ChronosInstantRecall();
    ChronosInstantRecallConfig config = new ChronosInstantRecallConfig();
    config.consumeClock = true;
    adaptation.setConfig(config);
    Player player = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    ItemStack mainHand = mock(ItemStack.class);
    ItemStack offHand = mock(ItemStack.class);
    when(player.getInventory()).thenReturn(inventory);
    when(inventory.getItemInMainHand()).thenReturn(mainHand);
    when(inventory.getItemInOffHand()).thenReturn(offHand);
    when(mainHand.getType()).thenReturn(Material.AIR);
    when(offHand.getType()).thenReturn(Material.AIR);

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
    ChronosInstantRecall adaptation = new ChronosInstantRecall();
    Player player = mock(Player.class);
    UUID playerId = UUID.randomUUID();
    AtomicBoolean cleaned = new AtomicBoolean(false);
    rewindCleanups(adaptation).put(
        playerId,
        new ChronosInstantRecall.RewindVisualState(player, () -> cleaned.set(true))
    );

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(same(player), any(Runnable.class))).thenAnswer(invocation -> {
        Runnable cleanup = invocation.getArgument(1);
        cleanup.run();
        return true;
      });

      adaptation.unregister();

      scheduling.verify(() -> J.runEntity(same(player), any(Runnable.class)), times(1));
    }

    assertThat(cleaned).isTrue();
    assertThat(rewindCleanups(adaptation)).isEmpty();
  }

  @Test
  void instantRecallRuntimeActivationAcceptsRewindsAgain() throws Exception {
    ChronosInstantRecall adaptation = new ChronosInstantRecall();
    AtomicBoolean accepting = acceptingRewinds(adaptation);
    accepting.set(false);

    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
      adaptation.onRuntimeActivated();
    }

    String source = Files.readString(INSTANT_RECALL_SOURCE);
    String activation = method(source, "protected void onRuntimeActivated()", "public void unregister()");
    assertThat(activation).contains("acceptingRewinds.set(true)");
    assertThat(accepting).isTrue();
  }

  @Test
  void temporalEchoRevalidatesTheCurrentActiveLevel() {
    ChronosTemporalEcho adaptation = mock(ChronosTemporalEcho.class, CALLS_REAL_METHODS);
    Player player = mock(Player.class);
    when(player.isOnline()).thenReturn(true);
    when(player.isDead()).thenReturn(false);
    when(adaptation.getActiveLevel(player)).thenReturn(3, 0);

    assertThat(adaptation.resolveDelayedEchoLevel(player)).isEqualTo(3);
    assertThat(adaptation.resolveDelayedEchoLevel(player)).isZero();

    when(player.isOnline()).thenReturn(false);
    assertThat(adaptation.resolveDelayedEchoLevel(player)).isZero();
  }

  @Test
  void temporalEchoHitReadsShooterStateOnlyOnTheShooterOwner() throws Exception {
    String source = Files.readString(TEMPORAL_ECHO_SOURCE);
    String hit = method(
        source,
        "public void on(ProjectileHitEvent e)",
        "private void rewardEchoHit"
    );
    String reward = method(source, "private void rewardEchoHit", "private void spawnEcho");

    assertThat(hit)
        .contains(
            "target.setNoDamageTicks(0)",
            "target.setLastDamage(0.0D)",
            "J.runEntity(shooter, () -> rewardEchoHit(shooter))"
        )
        .doesNotContain(
            "shooter.isOnline()",
            "hasActiveAdaptation(shooter)",
            "addStat(shooter"
        );
    assertThat(reward).contains(
        "resolveDelayedEchoLevel(shooter)",
        "addStat(shooter, \"chronos.temporal-echo.echo-hits\", 1)"
    );
  }

  @Test
  void timeBombQuitRestoresAndForgetsFrozenPlayerState() throws Exception {
    try (MockedStatic<ChronoTimeBombItem> itemFactory = mockStatic(ChronoTimeBombItem.class)) {
      itemFactory.when(ChronoTimeBombItem::withData).thenReturn(mock(ItemStack.class));
      ChronosTimeBomb adaptation = new ChronosTimeBomb();
      adaptation.setConfig(new ChronosTimeBomb.Config());
      Player player = mock(Player.class);
      PersistentDataContainer data = mock(PersistentDataContainer.class);
      UUID playerId = UUID.randomUUID();
      when(player.getUniqueId()).thenReturn(playerId);
      when(player.getPersistentDataContainer()).thenReturn(data);
      when(player.getAllowFlight()).thenReturn(false);
      when(player.isFlying()).thenReturn(false);
      when(player.hasGravity()).thenReturn(true);
      when(player.isOnGround()).thenReturn(false);
      freezePlayer(adaptation, player);

      PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
      when(quit.getPlayer()).thenReturn(player);
      adaptation.on(quit);

      InOrder order = inOrder(player);
      order.verify(player).setAllowFlight(true);
      order.verify(player).setFlying(true);
      order.verify(player).setGravity(false);
      order.verify(player).setGravity(true);
      order.verify(player).setFlying(false);
      order.verify(player).setAllowFlight(false);
      verify(data).remove(any(NamespacedKey.class));
      assertThat(frozenPlayers(adaptation)).doesNotContainKey(playerId);
    }
  }

  @Test
  void timeBombUnregisterRestoresFrozenEntitiesOnTheirOwner() throws Exception {
    try (MockedStatic<ChronoTimeBombItem> itemFactory = mockStatic(ChronoTimeBombItem.class)) {
      itemFactory.when(ChronoTimeBombItem::withData).thenReturn(mock(ItemStack.class));
      ChronosTimeBomb adaptation = new ChronosTimeBomb();
      adaptation.setConfig(new ChronosTimeBomb.Config());
      Entity entity = mock(Entity.class);
      PersistentDataContainer data = mock(PersistentDataContainer.class);
      UUID entityId = UUID.randomUUID();
      Vector initialVelocity = new Vector(0.25D, 0.5D, -0.25D);
      when(entity.getUniqueId()).thenReturn(entityId);
      when(entity.getPersistentDataContainer()).thenReturn(data);
      when(entity.getVelocity()).thenReturn(initialVelocity);
      when(entity.hasGravity()).thenReturn(true);
      freezeEntity(adaptation, entity);

      try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
           MockedStatic<J> scheduling = mockStatic(J.class)) {
        bukkit.when(() -> Bukkit.getEntity(entityId)).thenReturn(entity);
        scheduling.when(() -> J.runEntity(same(entity), any(Runnable.class))).thenAnswer(invocation -> {
          Runnable restoration = invocation.getArgument(1);
          restoration.run();
          return true;
        });

        adaptation.unregister();

        scheduling.verify(() -> J.runEntity(same(entity), any(Runnable.class)), times(1));
      }

      verify(entity).setGravity(true);
      verify(entity).setVelocity(initialVelocity);
      verify(data).remove(any(NamespacedKey.class));
      assertThat(frozenEntities(adaptation)).isEmpty();
    }
  }

  private static void freezePlayer(ChronosTimeBomb adaptation, Player player) throws Exception {
    Method method = ChronosTimeBomb.class.getDeclaredMethod("freezePlayer", Player.class, UUID.class);
    method.setAccessible(true);
    method.invoke(adaptation, player, UUID.randomUUID());
  }

  private static void freezeEntity(ChronosTimeBomb adaptation, Entity entity) throws Exception {
    Method method = ChronosTimeBomb.class.getDeclaredMethod("freezeEntity", Entity.class, UUID.class);
    method.setAccessible(true);
    method.invoke(adaptation, entity, UUID.randomUUID());
  }

  private static void restoreStampedRewindState(
      ChronosInstantRecall adaptation,
      Player player
  ) throws Exception {
    Method method = ChronosInstantRecall.class.getDeclaredMethod(
        "restoreStampedRewindState",
        Player.class
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
