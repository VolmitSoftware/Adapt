package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.projectile.ProjectileReplacementRegistry;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AxeThrowingAxeRecoveryTest extends AdaptTestBase {
  @BeforeEach
  void configurePluginName() {
    lenient().when(plugin.getName()).thenReturn("Adapt");
    lenient().when(plugin.namespace()).thenReturn("adapt");
  }

  @AfterEach
  void clearReplacementClaims() {
    ProjectileReplacementRegistry.clear();
  }

  @Test
  void acceptedShutdownTasksLeaveDurableRecoveryUntilTheyExecute() throws Exception {
    AxeThrowingAxe adaptation = new AxeThrowingAxe();
    UUID ownerId = UUID.randomUUID();
    UUID projectileId = UUID.randomUUID();
    Player owner = mock(Player.class);
    Entity projectile = mock(Entity.class);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    ItemStack axe = mock(ItemStack.class);
    NamespacedKey recoveryKey = new NamespacedKey(
        plugin,
        "throwing_axe_recovery_" + projectileId.toString().replace("-", "")
    );
    when(owner.isOnline()).thenReturn(true);
    when(owner.getPersistentDataContainer()).thenReturn(data);
    when(axe.clone()).thenReturn(axe);
    inFlight(adaptation).put(
        projectileId,
        new AxeThrowingAxe.ThrownAxe(
            ownerId,
            axe,
            9D,
            true,
            false,
            true,
            recoveryKey,
            AxeThrowingAxe.flightDeadline(System.nanoTime(), 80L)
        )
    );

    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
         MockedStatic<J> scheduling = mockStatic(J.class)) {
      bukkit.when(() -> Bukkit.getEntity(projectileId)).thenReturn(projectile);
      bukkit.when(() -> Bukkit.getPlayer(ownerId)).thenReturn(owner);
      scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class))).thenReturn(true);

      adaptation.unregister();

      scheduling.verify(() -> J.runEntity(same(projectile), any(Runnable.class)), times(1));
      scheduling.verify(() -> J.runEntity(same(owner), any(Runnable.class)), times(1));
    }

    verify(data, never()).remove(recoveryKey);
    assertThat(inFlight(adaptation)).isEmpty();
  }

  @Test
  void ricochetReplacementMovesTheSameThrowAndKeepsItsOriginalDeadline() throws Exception {
    AxeThrowingAxe adaptation = new AxeThrowingAxe();
    UUID ownerId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();
    UUID replacementId = UUID.randomUUID();
    ItemStack axe = mock(ItemStack.class);
    Snowball source = mock(Snowball.class);
    Snowball replacement = mock(Snowball.class);
    NamespacedKey recoveryKey = new NamespacedKey(
        plugin,
        "throwing_axe_recovery_" + sourceId.toString().replace("-", "")
    );
    long deadline = AxeThrowingAxe.flightDeadline(System.nanoTime(), 80L);
    AxeThrowingAxe.ThrownAxe thrown = new AxeThrowingAxe.ThrownAxe(
        ownerId,
        axe,
        9D,
        true,
        false,
        true,
        recoveryKey,
        deadline
    );
    when(axe.clone()).thenReturn(axe);
    when(source.getUniqueId()).thenReturn(sourceId);
    when(source.getLocation()).thenReturn(new Location(null, 1D, 2D, 3D));
    when(replacement.getUniqueId()).thenReturn(replacementId);

    registerThrow(adaptation, source, thrown);
    ProjectileReplacementRegistry.Ticket ticket = ProjectileReplacementRegistry.begin(source);

    try (MockedStatic<FoliaScheduler> scheduling = mockStatic(FoliaScheduler.class)) {
      scheduling.when(() -> FoliaScheduler.runEntity(
          same(plugin),
          same(replacement),
          any(Runnable.class),
          anyLong()
      )).thenReturn(true);

      assertThat(ticket).isNotNull();
      assertThat(inFlight(adaptation)).doesNotContainKey(sourceId);
      assertThat(ticket.complete(replacement)).isTrue();
    }

    assertThat(inFlight(adaptation)).containsEntry(replacementId, thrown);
    assertThat(inFlight(adaptation).get(replacementId).recoveryKey()).isEqualTo(recoveryKey);
    assertThat(inFlight(adaptation).get(replacementId).expiresAtNanos()).isEqualTo(deadline);
    assertThat(pendingReplacements(adaptation)).isEmpty();
  }

  @Test
  void rejectedReplacementCanBeCancelledExactlyOnceWithoutLeakingState() throws Exception {
    AxeThrowingAxe adaptation = new AxeThrowingAxe();
    UUID sourceId = UUID.randomUUID();
    UUID replacementId = UUID.randomUUID();
    ItemStack axe = mock(ItemStack.class);
    Snowball source = mock(Snowball.class);
    Snowball replacement = mock(Snowball.class);
    AxeThrowingAxe.ThrownAxe thrown = new AxeThrowingAxe.ThrownAxe(
        UUID.randomUUID(),
        axe,
        9D,
        true,
        false,
        false,
        new NamespacedKey(plugin, "throwing_axe_recovery_" + sourceId),
        AxeThrowingAxe.flightDeadline(System.nanoTime(), 80L)
    );
    when(axe.clone()).thenReturn(axe);
    when(source.getUniqueId()).thenReturn(sourceId);
    when(source.getLocation()).thenReturn(new Location(null, 1D, 2D, 3D));
    when(replacement.getUniqueId()).thenReturn(replacementId);

    registerThrow(adaptation, source, thrown);
    ProjectileReplacementRegistry.Ticket ticket = ProjectileReplacementRegistry.begin(source);

    try (MockedStatic<FoliaScheduler> scheduling = mockStatic(FoliaScheduler.class)) {
      scheduling.when(() -> FoliaScheduler.runEntity(
          same(plugin),
          same(replacement),
          any(Runnable.class),
          anyLong()
      )).thenReturn(false);

      assertThat(ticket.complete(replacement)).isFalse();
    }

    assertThat(inFlight(adaptation)).isEmpty();
    assertThat(pendingReplacements(adaptation)).hasSize(1);
    ticket.cancel();
    ticket.cancel();
    assertThat(pendingReplacements(adaptation)).isEmpty();
  }

  @Test
  void remainingFlightTimeNeverResetsAcrossBounces() {
    long deadline = AxeThrowingAxe.flightDeadline(1_000_000_000L, 80L);

    assertThat(AxeThrowingAxe.remainingFlightTicks(deadline, 1_000_000_000L))
        .isEqualTo(80L);
    assertThat(AxeThrowingAxe.remainingFlightTicks(deadline, 2_025_000_000L))
        .isEqualTo(60L);
    assertThat(AxeThrowingAxe.remainingFlightTicks(deadline, deadline)).isZero();
  }

  @Test
  void cancelledProjectileHitsDoNotResolveTheThrow() throws Exception {
    Method handler = AxeThrowingAxe.class.getDeclaredMethod("on", ProjectileHitEvent.class);
    EventHandler registration = handler.getAnnotation(EventHandler.class);

    assertThat(registration).isNotNull();
    assertThat(registration.ignoreCancelled()).isTrue();
  }

  @Test
  void cancellingQueuedImpactDropRestoresStampAndSuppressesLateDelivery() throws Exception {
    AxeThrowingAxe adaptation = new AxeThrowingAxe();
    UUID ownerId = UUID.randomUUID();
    UUID projectileId = UUID.randomUUID();
    Player owner = mock(Player.class);
    World world = mock(World.class);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    ItemStack axe = mock(ItemStack.class);
    Location impact = new Location(world, 1D, 2D, 3D);
    NamespacedKey recoveryKey = new NamespacedKey(
        plugin,
        "throwing_axe_recovery_" + projectileId.toString().replace("-", "")
    );
    byte[] encoded = new byte[]{4, 8, 15, 16, 23, 42};
    AtomicReference<byte[]> stamp = new AtomicReference<>(encoded);
    AtomicReference<Runnable> delivery = new AtomicReference<>();
    when(owner.getUniqueId()).thenReturn(ownerId);
    when(owner.getPersistentDataContainer()).thenReturn(data);
    when(axe.clone()).thenReturn(axe);
    when(data.get(recoveryKey, PersistentDataType.BYTE_ARRAY))
        .thenAnswer(invocation -> stamp.get());
    doAnswer(invocation -> {
      stamp.set(null);
      return null;
    }).when(data).remove(recoveryKey);
    doAnswer(invocation -> {
      stamp.set(((byte[]) invocation.getArgument(2)).clone());
      return null;
    }).when(data).set(
        eq(recoveryKey),
        same(PersistentDataType.BYTE_ARRAY),
        any(byte[].class)
    );
    AxeThrowingAxe.ThrownAxe thrown = new AxeThrowingAxe.ThrownAxe(
        ownerId,
        axe,
        9D,
        false,
        false,
        true,
        recoveryKey,
        AxeThrowingAxe.flightDeadline(System.nanoTime(), 80L)
    );

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runAt(same(impact), any(Runnable.class)))
          .thenAnswer(invocation -> {
            delivery.set(invocation.getArgument(1));
            return true;
          });
      scheduling.when(() -> J.isOwnedByCurrentRegion(same(owner))).thenReturn(true);

      invokeDrop(adaptation, impact, thrown, owner);
      assertThat(stamp.get()).isNull();
      assertThat(pendingDrops(adaptation)).hasSize(1);

      Object pending = pendingDrops(adaptation).iterator().next();
      Method cancel = pending.getClass().getDeclaredMethod("cancel", Player.class);
      cancel.setAccessible(true);
      cancel.invoke(pending, owner);
      delivery.get().run();
    }

    assertThat(stamp.get()).containsExactly(encoded);
    assertThat(pendingDrops(adaptation)).isEmpty();
    verify(world, never()).dropItem(any(Location.class), any(ItemStack.class));
  }

  @Test
  void rejectedOwnerRestorationJournalsTheReservedAxeBeforeTeardown() throws Exception {
    AxeThrowingAxe adaptation = new AxeThrowingAxe();
    UUID ownerId = UUID.randomUUID();
    UUID projectileId = UUID.randomUUID();
    Player owner = mock(Player.class);
    World world = mock(World.class);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    ItemStack axe = mock(ItemStack.class);
    Location impact = new Location(world, 1D, 2D, 3D);
    NamespacedKey recoveryKey = new NamespacedKey(
        plugin,
        "throwing_axe_recovery_" + projectileId.toString().replace("-", "")
    );
    byte[] encoded = new byte[]{4, 8, 15, 16, 23, 42};
    AtomicReference<byte[]> stamp = new AtomicReference<>(encoded);
    AtomicReference<Runnable> delivery = new AtomicReference<>();
    when(owner.getUniqueId()).thenReturn(ownerId);
    when(owner.getPersistentDataContainer()).thenReturn(data);
    when(axe.clone()).thenReturn(axe);
    when(data.get(recoveryKey, PersistentDataType.BYTE_ARRAY))
        .thenAnswer(invocation -> stamp.get());
    doAnswer(invocation -> {
      stamp.set(null);
      return null;
    }).when(data).remove(recoveryKey);
    AxeThrowingAxe.ThrownAxe thrown = new AxeThrowingAxe.ThrownAxe(
        ownerId,
        axe,
        9D,
        false,
        false,
        true,
        recoveryKey,
        AxeThrowingAxe.flightDeadline(System.nanoTime(), 80L)
    );

    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
         MockedStatic<J> scheduling = mockStatic(J.class);
         MockedStatic<FoliaScheduler> folia = mockStatic(FoliaScheduler.class)) {
      bukkit.when(() -> Bukkit.getPlayer(ownerId)).thenReturn(owner);
      scheduling.when(() -> J.runAt(same(impact), any(Runnable.class)))
          .thenAnswer(invocation -> {
            delivery.set(invocation.getArgument(1));
            return true;
          });
      scheduling.when(() -> J.isOwnedByCurrentRegion(same(owner))).thenReturn(false);
      folia.when(() -> FoliaScheduler.runEntity(
          same(plugin),
          same(owner),
          any(Runnable.class),
          eq(0L),
          any(Runnable.class)
      )).thenReturn(false);

      invokeDrop(adaptation, impact, thrown, owner);
      Object pending = pendingDrops(adaptation).iterator().next();
      Method cancel = pending.getClass().getDeclaredMethod("cancel", Player.class);
      cancel.setAccessible(true);
      cancel.invoke(pending, new Object[]{null});
      delivery.get().run();
    }

    AxeRecoveryJournal journal = recoveryJournal(adaptation);
    assertThat(stamp.get()).isNull();
    assertThat(pendingDrops(adaptation)).isEmpty();
    assertThat(journal.keys(ownerId)).containsExactly(recoveryKey.getKey());
    assertThat(journal.read(ownerId, recoveryKey.getKey())).containsExactly(encoded);
    verify(world, never()).dropItem(any(Location.class), any(ItemStack.class));
  }

  @Test
  void journalImportStagesTheStampBeforeDeletingTheDurableEntry() throws Exception {
    AxeThrowingAxe adaptation = new AxeThrowingAxe();
    AxeRecoveryJournal journal = recoveryJournal(adaptation);
    UUID ownerId = UUID.randomUUID();
    Player owner = mock(Player.class);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    NamespacedKey recoveryKey = new NamespacedKey(
        plugin,
        "throwing_axe_recovery_12345678"
    );
    byte[] encoded = new byte[]{4, 8, 15, 16, 23, 42};
    AtomicReference<byte[]> stamp = new AtomicReference<>();
    when(owner.getUniqueId()).thenReturn(ownerId);
    when(owner.getPersistentDataContainer()).thenReturn(data);
    when(data.get(recoveryKey, PersistentDataType.BYTE_ARRAY))
        .thenAnswer(invocation -> stamp.get());
    doAnswer(invocation -> {
      stamp.set(((byte[]) invocation.getArgument(2)).clone());
      return null;
    }).when(data).set(
        eq(recoveryKey),
        same(PersistentDataType.BYTE_ARRAY),
        any(byte[].class)
    );
    journal.persist(ownerId, recoveryKey, encoded);

    NamespacedKey imported = invokeJournalStage(adaptation, owner, recoveryKey.getKey());

    assertThat(imported).isEqualTo(recoveryKey);
    assertThat(stamp.get()).containsExactly(encoded);
    assertThat(journal.keys(ownerId)).isEmpty();
  }

  @Test
  void conflictingJournalAndStampAreBothRetainedWithoutDelivery() throws Exception {
    AxeThrowingAxe adaptation = new AxeThrowingAxe();
    AxeRecoveryJournal journal = recoveryJournal(adaptation);
    UUID ownerId = UUID.randomUUID();
    Player owner = mock(Player.class);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    NamespacedKey recoveryKey = new NamespacedKey(
        plugin,
        "throwing_axe_recovery_87654321"
    );
    byte[] journaled = new byte[]{4, 8, 15, 16, 23, 42};
    byte[] stamped = new byte[]{1, 2, 3};
    when(owner.isOnline()).thenReturn(true);
    when(owner.getUniqueId()).thenReturn(ownerId);
    when(owner.getPersistentDataContainer()).thenReturn(data);
    when(data.getKeys()).thenReturn(Set.of(recoveryKey));
    when(data.get(recoveryKey, PersistentDataType.BYTE_ARRAY)).thenReturn(stamped);
    journal.persist(ownerId, recoveryKey, journaled);

    invokeRecoverAll(adaptation, owner);

    verify(data, never()).remove(recoveryKey);
    assertThat(journal.keys(ownerId)).containsExactly(recoveryKey.getKey());
    assertThat(journal.read(ownerId, recoveryKey.getKey())).containsExactly(journaled);
  }

  @SuppressWarnings("unchecked")
  private static Map<UUID, AxeThrowingAxe.ThrownAxe> inFlight(
      AxeThrowingAxe adaptation
  ) throws Exception {
    Field field = AxeThrowingAxe.class.getDeclaredField("inFlight");
    field.setAccessible(true);
    return (Map<UUID, AxeThrowingAxe.ThrownAxe>) field.get(adaptation);
  }

  @SuppressWarnings("unchecked")
  private static Set<Object> pendingReplacements(AxeThrowingAxe adaptation) throws Exception {
    Field field = AxeThrowingAxe.class.getDeclaredField("pendingReplacements");
    field.setAccessible(true);
    return (Set<Object>) field.get(adaptation);
  }

  @SuppressWarnings("unchecked")
  private static Set<Object> pendingDrops(AxeThrowingAxe adaptation) throws Exception {
    Field field = AxeThrowingAxe.class.getDeclaredField("pendingDrops");
    field.setAccessible(true);
    return Set.copyOf(((Map<NamespacedKey, Object>) field.get(adaptation)).values());
  }

  private static AxeRecoveryJournal recoveryJournal(AxeThrowingAxe adaptation) throws Exception {
    Field field = AxeThrowingAxe.class.getDeclaredField("recoveryJournal");
    field.setAccessible(true);
    return (AxeRecoveryJournal) field.get(adaptation);
  }

  private static void registerThrow(
      AxeThrowingAxe adaptation,
      Snowball projectile,
      AxeThrowingAxe.ThrownAxe thrown
  ) throws Exception {
    Method method = AxeThrowingAxe.class.getDeclaredMethod(
        "registerThrow",
        Snowball.class,
        AxeThrowingAxe.ThrownAxe.class
    );
    method.setAccessible(true);
    method.invoke(adaptation, projectile, thrown);
  }

  private static void invokeDrop(
      AxeThrowingAxe adaptation,
      Location impact,
      AxeThrowingAxe.ThrownAxe thrown,
      Player owner
  ) throws Exception {
    Method method = AxeThrowingAxe.class.getDeclaredMethod(
        "dropAxe",
        Location.class,
        AxeThrowingAxe.ThrownAxe.class,
        Player.class
    );
    method.setAccessible(true);
    method.invoke(adaptation, impact, thrown, owner);
  }

  private static NamespacedKey invokeJournalStage(
      AxeThrowingAxe adaptation,
      Player owner,
      String recoveryKey
  ) throws Exception {
    Method method = AxeThrowingAxe.class.getDeclaredMethod(
        "stageJournaledAxe",
        Player.class,
        String.class
    );
    method.setAccessible(true);
    return (NamespacedKey) method.invoke(adaptation, owner, recoveryKey);
  }

  private static void invokeRecoverAll(
      AxeThrowingAxe adaptation,
      Player owner
  ) throws Exception {
    Method method = AxeThrowingAxe.class.getDeclaredMethod("recoverStampedAxes", Player.class);
    method.setAccessible(true);
    method.invoke(adaptation, owner);
  }
}
