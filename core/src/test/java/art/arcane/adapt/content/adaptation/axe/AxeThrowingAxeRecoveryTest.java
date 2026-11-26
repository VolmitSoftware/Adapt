package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.projectile.ProjectileReplacementRegistry;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.testkit.MemoryData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AxeThrowingAxeRecoveryTest extends AdaptTestBase {
  private static final Id SNOWBALL = Id.minecraft("snowball");

  private PlatformHost host;
  private AdaptScheduler scheduler;

  @BeforeEach
  void captureHosts() {
    host = core.getPlatform().host();
    scheduler = core.getPlatform().scheduler();
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
    PlayerRef owner = mock(PlayerRef.class);
    EntityRef projectile = mock(EntityRef.class);
    DataHolder data = mock(DataHolder.class);
    ItemRef axe = mock(ItemRef.class);
    Id recoveryKey = Id.adapt(
        "throwing_axe_recovery_" + projectileId.toString().replace("-", "")
    );
    when(owner.isOnline()).thenReturn(true);
    when(owner.data()).thenReturn(data);
    when(axe.copy()).thenReturn(axe);
    inFlight(adaptation).put(
        projectileId,
        new AxeThrow.ThrownAxe(
            ownerId,
            axe,
            9D,
            true,
            false,
            true,
            recoveryKey,
            AxeThrow.flightDeadline(System.nanoTime(), 80L)
        )
    );

    when(host.entity(projectileId)).thenReturn(projectile);
    when(host.player(ownerId)).thenReturn(owner);
    when(scheduler.runEntity(any(EntityRef.class), any(Runnable.class), anyLong(), any())).thenReturn(true);

    adaptation.unregister();

    verify(scheduler, times(1)).runEntity(same(projectile), any(Runnable.class), eq(0L), isNull());
    verify(scheduler, times(1)).runEntity(same(owner), any(Runnable.class), eq(0L), isNull());
    verify(data, never()).remove(recoveryKey);
    assertThat(inFlight(adaptation)).isEmpty();
  }

  @Test
  void ricochetReplacementMovesTheSameThrowAndKeepsItsOriginalDeadline() throws Exception {
    AxeThrowingAxe adaptation = new AxeThrowingAxe();
    UUID ownerId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();
    UUID replacementId = UUID.randomUUID();
    ItemRef axe = mock(ItemRef.class);
    ProjectileRef source = snowball(sourceId);
    ProjectileRef replacement = snowball(replacementId);
    Id recoveryKey = Id.adapt(
        "throwing_axe_recovery_" + sourceId.toString().replace("-", "")
    );
    long deadline = AxeThrow.flightDeadline(System.nanoTime(), 80L);
    when(axe.copy()).thenReturn(axe);
    AxeThrow.ThrownAxe thrown = new AxeThrow.ThrownAxe(
        ownerId,
        axe,
        9D,
        true,
        false,
        true,
        recoveryKey,
        deadline
    );
    Loc sourceLocation = new Loc(mock(WorldRef.class), 1D, 2D, 3D);
    when(source.location()).thenReturn(sourceLocation);

    registerThrow(adaptation, source, thrown);
    ProjectileReplacementRegistry.Ticket ticket = ProjectileReplacementRegistry.begin(source);

    when(scheduler.runEntity(same(replacement), any(Runnable.class), anyLong(), isNull())).thenReturn(true);

    assertThat(ticket).isNotNull();
    assertThat(inFlight(adaptation)).doesNotContainKey(sourceId);
    assertThat(ticket.complete(replacement)).isTrue();

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
    ItemRef axe = mock(ItemRef.class);
    ProjectileRef source = snowball(sourceId);
    ProjectileRef replacement = snowball(replacementId);
    when(axe.copy()).thenReturn(axe);
    AxeThrow.ThrownAxe thrown = new AxeThrow.ThrownAxe(
        UUID.randomUUID(),
        axe,
        9D,
        true,
        false,
        false,
        Id.adapt("throwing_axe_recovery_" + sourceId),
        AxeThrow.flightDeadline(System.nanoTime(), 80L)
    );
    Loc sourceLocation = new Loc(mock(WorldRef.class), 1D, 2D, 3D);
    when(source.location()).thenReturn(sourceLocation);

    registerThrow(adaptation, source, thrown);
    ProjectileReplacementRegistry.Ticket ticket = ProjectileReplacementRegistry.begin(source);

    when(scheduler.runEntity(same(replacement), any(Runnable.class), anyLong(), isNull())).thenReturn(false);

    assertThat(ticket.complete(replacement)).isFalse();

    assertThat(inFlight(adaptation)).isEmpty();
    assertThat(pendingReplacements(adaptation)).hasSize(1);
    ticket.cancel();
    ticket.cancel();
    assertThat(pendingReplacements(adaptation)).isEmpty();
  }

  @Test
  void remainingFlightTimeNeverResetsAcrossBounces() {
    long deadline = AxeThrow.flightDeadline(1_000_000_000L, 80L);

    assertThat(AxeThrow.remainingFlightTicks(deadline, 1_000_000_000L))
        .isEqualTo(80L);
    assertThat(AxeThrow.remainingFlightTicks(deadline, 2_025_000_000L))
        .isEqualTo(60L);
    assertThat(AxeThrow.remainingFlightTicks(deadline, deadline)).isZero();
  }

  @Test
  void cancelledProjectileHitsDoNotResolveTheThrow() throws Exception {
    Method handler = AxeThrowingAxe.class.getDeclaredMethod("on", ProjectileHitEvent.class);
    Handle registration = handler.getAnnotation(Handle.class);

    assertThat(registration).isNotNull();
    assertThat(registration.ignoreCancelled()).isTrue();
    assertThat(handler.isAnnotationPresent(ReceiveCancelledEvents.class)).isFalse();
  }

  @Test
  void cancellingQueuedImpactDropRestoresStampAndSuppressesLateDelivery() throws Exception {
    AxeThrowingAxe adaptation = new AxeThrowingAxe();
    UUID ownerId = UUID.randomUUID();
    UUID projectileId = UUID.randomUUID();
    PlayerRef owner = mock(PlayerRef.class);
    WorldRef world = mock(WorldRef.class);
    MemoryData data = new MemoryData();
    ItemRef axe = mock(ItemRef.class);
    Loc impact = new Loc(world, 1D, 2D, 3D);
    Id recoveryKey = Id.adapt(
        "throwing_axe_recovery_" + projectileId.toString().replace("-", "")
    );
    DataKey<byte[]> stamp = DataKey.of(recoveryKey, DataCodecs.BYTE_ARRAY);
    byte[] encoded = new byte[]{4, 8, 15, 16, 23, 42};
    data.set(stamp, encoded);
    AtomicReference<Runnable> delivery = new AtomicReference<>();
    when(owner.uuid()).thenReturn(ownerId);
    when(owner.data()).thenReturn(data);
    when(axe.copy()).thenReturn(axe);
    AxeThrow.ThrownAxe thrown = new AxeThrow.ThrownAxe(
        ownerId,
        axe,
        9D,
        false,
        false,
        true,
        recoveryKey,
        AxeThrow.flightDeadline(System.nanoTime(), 80L)
    );

    when(scheduler.runAt(same(impact), any(Runnable.class), anyLong()))
        .thenAnswer(invocation -> {
          delivery.set(invocation.getArgument(1));
          return true;
        });
    when(scheduler.owns(same(owner))).thenReturn(true);

    invokeDrop(adaptation, impact, thrown, owner);
    assertThat(data.get(stamp)).isNull();
    assertThat(pendingDrops(adaptation)).hasSize(1);

    Object pending = pendingDrops(adaptation).iterator().next();
    Method cancel = pending.getClass().getDeclaredMethod("cancel", PlayerRef.class);
    cancel.setAccessible(true);
    cancel.invoke(pending, owner);
    delivery.get().run();

    assertThat(data.get(stamp)).containsExactly(encoded);
    assertThat(pendingDrops(adaptation)).isEmpty();
    verify(world, never()).dropItem(any(Vec3.class), any(ItemRef.class));
  }

  @Test
  void rejectedOwnerRestorationJournalsTheReservedAxeBeforeTeardown() throws Exception {
    AxeThrowingAxe adaptation = new AxeThrowingAxe();
    UUID ownerId = UUID.randomUUID();
    UUID projectileId = UUID.randomUUID();
    PlayerRef owner = mock(PlayerRef.class);
    WorldRef world = mock(WorldRef.class);
    MemoryData data = new MemoryData();
    ItemRef axe = mock(ItemRef.class);
    Loc impact = new Loc(world, 1D, 2D, 3D);
    Id recoveryKey = Id.adapt(
        "throwing_axe_recovery_" + projectileId.toString().replace("-", "")
    );
    DataKey<byte[]> stamp = DataKey.of(recoveryKey, DataCodecs.BYTE_ARRAY);
    byte[] encoded = new byte[]{4, 8, 15, 16, 23, 42};
    data.set(stamp, encoded);
    AtomicReference<Runnable> delivery = new AtomicReference<>();
    when(owner.uuid()).thenReturn(ownerId);
    when(owner.data()).thenReturn(data);
    when(axe.copy()).thenReturn(axe);
    AxeThrow.ThrownAxe thrown = new AxeThrow.ThrownAxe(
        ownerId,
        axe,
        9D,
        false,
        false,
        true,
        recoveryKey,
        AxeThrow.flightDeadline(System.nanoTime(), 80L)
    );

    when(host.player(ownerId)).thenReturn(owner);
    when(scheduler.runAt(same(impact), any(Runnable.class), anyLong()))
        .thenAnswer(invocation -> {
          delivery.set(invocation.getArgument(1));
          return true;
        });
    when(scheduler.owns(same(owner))).thenReturn(false);
    when(scheduler.runEntity(same(owner), any(Runnable.class), eq(0L), any(Runnable.class))).thenReturn(false);

    invokeDrop(adaptation, impact, thrown, owner);
    Object pending = pendingDrops(adaptation).iterator().next();
    Method cancel = pending.getClass().getDeclaredMethod("cancel", PlayerRef.class);
    cancel.setAccessible(true);
    cancel.invoke(pending, new Object[]{null});
    delivery.get().run();

    AxeRecoveryJournal journal = recoveryJournal(adaptation);
    assertThat(data.get(stamp)).isNull();
    assertThat(pendingDrops(adaptation)).isEmpty();
    assertThat(journal.keys(ownerId)).containsExactly(recoveryKey.path());
    assertThat(journal.read(ownerId, recoveryKey.path())).containsExactly(encoded);
    verify(world, never()).dropItem(any(Vec3.class), any(ItemRef.class));
  }

  @Test
  void journalImportStagesTheStampBeforeDeletingTheDurableEntry() throws Exception {
    AxeThrowingAxe adaptation = new AxeThrowingAxe();
    AxeRecoveryJournal journal = recoveryJournal(adaptation);
    UUID ownerId = UUID.randomUUID();
    PlayerRef owner = mock(PlayerRef.class);
    MemoryData data = new MemoryData();
    Id recoveryKey = Id.adapt("throwing_axe_recovery_12345678");
    DataKey<byte[]> stamp = DataKey.of(recoveryKey, DataCodecs.BYTE_ARRAY);
    byte[] encoded = new byte[]{4, 8, 15, 16, 23, 42};
    when(owner.uuid()).thenReturn(ownerId);
    when(owner.data()).thenReturn(data);
    journal.persist(ownerId, recoveryKey, encoded);

    Id imported = invokeJournalStage(adaptation, owner, recoveryKey.path());

    assertThat(imported).isEqualTo(recoveryKey);
    assertThat(data.get(stamp)).containsExactly(encoded);
    assertThat(journal.keys(ownerId)).isEmpty();
  }

  @Test
  void conflictingJournalAndStampAreBothRetainedWithoutDelivery() throws Exception {
    AxeThrowingAxe adaptation = new AxeThrowingAxe();
    AxeRecoveryJournal journal = recoveryJournal(adaptation);
    UUID ownerId = UUID.randomUUID();
    PlayerRef owner = mock(PlayerRef.class);
    MemoryData data = new MemoryData();
    Id recoveryKey = Id.adapt("throwing_axe_recovery_87654321");
    DataKey<byte[]> stamp = DataKey.of(recoveryKey, DataCodecs.BYTE_ARRAY);
    byte[] journaled = new byte[]{4, 8, 15, 16, 23, 42};
    byte[] stamped = new byte[]{1, 2, 3};
    when(owner.isOnline()).thenReturn(true);
    when(owner.uuid()).thenReturn(ownerId);
    when(owner.data()).thenReturn(data);
    data.set(stamp, stamped);
    journal.persist(ownerId, recoveryKey, journaled);

    invokeRecoverAll(adaptation, owner);

    assertThat(data.get(stamp)).containsExactly(stamped);
    assertThat(journal.keys(ownerId)).containsExactly(recoveryKey.path());
    assertThat(journal.read(ownerId, recoveryKey.path())).containsExactly(journaled);
  }

  private static AxeThrow throwing(AxeThrowingAxe adaptation) throws Exception {
    Field field = AxeThrowingAxe.class.getDeclaredField("throwing");
    field.setAccessible(true);
    return (AxeThrow) field.get(adaptation);
  }

  private static ProjectileRef snowball(UUID id) {
    ProjectileRef projectile = mock(ProjectileRef.class);
    when(projectile.uuid()).thenReturn(id);
    when(projectile.type()).thenReturn(SNOWBALL);
    return projectile;
  }

  @SuppressWarnings("unchecked")
  private static Map<UUID, AxeThrow.ThrownAxe> inFlight(
      AxeThrowingAxe adaptation
  ) throws Exception {
    Field field = AxeThrow.class.getDeclaredField("inFlight");
    field.setAccessible(true);
    return (Map<UUID, AxeThrow.ThrownAxe>) field.get(throwing(adaptation));
  }

  @SuppressWarnings("unchecked")
  private static Set<Object> pendingReplacements(AxeThrowingAxe adaptation) throws Exception {
    Field field = AxeThrow.class.getDeclaredField("pendingReplacements");
    field.setAccessible(true);
    return (Set<Object>) field.get(throwing(adaptation));
  }

  @SuppressWarnings("unchecked")
  private static Set<Object> pendingDrops(AxeThrowingAxe adaptation) throws Exception {
    Field field = AxeThrow.class.getDeclaredField("pendingDrops");
    field.setAccessible(true);
    return Set.copyOf(((Map<Id, Object>) field.get(throwing(adaptation))).values());
  }

  private static AxeRecoveryJournal recoveryJournal(AxeThrowingAxe adaptation) throws Exception {
    Field field = AxeThrow.class.getDeclaredField("recoveryJournal");
    field.setAccessible(true);
    return (AxeRecoveryJournal) field.get(throwing(adaptation));
  }

  private static void registerThrow(
      AxeThrowingAxe adaptation,
      ProjectileRef projectile,
      AxeThrow.ThrownAxe thrown
  ) throws Exception {
    Method method = AxeThrow.class.getDeclaredMethod(
        "registerThrow",
        ProjectileRef.class,
        AxeThrow.ThrownAxe.class
    );
    method.setAccessible(true);
    method.invoke(throwing(adaptation), projectile, thrown);
  }

  private static void invokeDrop(
      AxeThrowingAxe adaptation,
      Loc impact,
      AxeThrow.ThrownAxe thrown,
      PlayerRef owner
  ) throws Exception {
    Method method = AxeThrow.class.getDeclaredMethod(
        "dropAxe",
        Loc.class,
        AxeThrow.ThrownAxe.class,
        PlayerRef.class
    );
    method.setAccessible(true);
    method.invoke(throwing(adaptation), impact, thrown, owner);
  }

  private static Id invokeJournalStage(
      AxeThrowingAxe adaptation,
      PlayerRef owner,
      String recoveryKey
  ) throws Exception {
    Method method = AxeThrow.class.getDeclaredMethod(
        "stageJournaledAxe",
        PlayerRef.class,
        String.class
    );
    method.setAccessible(true);
    return (Id) method.invoke(throwing(adaptation), owner, recoveryKey);
  }

  private static void invokeRecoverAll(
      AxeThrowingAxe adaptation,
      PlayerRef owner
  ) throws Exception {
    Method method = AxeThrow.class.getDeclaredMethod("recoverStampedAxes", PlayerRef.class);
    method.setAccessible(true);
    method.invoke(throwing(adaptation), owner);
  }
}
