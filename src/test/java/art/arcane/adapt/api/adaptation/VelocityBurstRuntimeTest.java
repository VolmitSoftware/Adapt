package art.arcane.adapt.api.adaptation;

import org.junit.jupiter.api.Test;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.entity.Player;
import org.mockito.MockedStatic;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.bukkit.util.Vector;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VelocityBurstRuntimeTest {
  @Test
  void accelerationBuildsAcrossNeutralServerVelocityAndBrakingResetsIt() throws Exception {
    Constructor<VelocityBurstRuntime> constructor = VelocityBurstRuntime.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    VelocityBurstRuntime runtime = constructor.newInstance();
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    when(player.getVelocity()).thenAnswer(invocation -> new Vector(0, -0.2, 0));
    VelocityBurstRuntime.Client client = client(runtime, mock(VelocityBurstRuntime.Feedback.class));
    Object bucket = bucket(player);
    ledger(bucket).put(client, session(client));
    Method accelerate = VelocityBurstRuntime.class.getDeclaredMethod("accelerateSessions", bucket.getClass(), Player.class, Vector.class, double.class);
    accelerate.setAccessible(true);
    Method brake = VelocityBurstRuntime.class.getDeclaredMethod("brakeSessions", bucket.getClass(), Player.class, double.class);
    brake.setAccessible(true);

    accelerate.invoke(runtime, bucket, player, new Vector(1, 0, 0), 1D);
    accelerate.invoke(runtime, bucket, player, new Vector(1, 0, 0), 1D);
    accelerate.invoke(runtime, bucket, player, new Vector(1, 0, 0), 1D);
    brake.invoke(runtime, bucket, player, 1D);
    brake.invoke(runtime, bucket, player, 1D);
    accelerate.invoke(runtime, bucket, player, new Vector(1, 0, 0), 1D);

    ArgumentCaptor<Vector> velocities = ArgumentCaptor.forClass(Vector.class);
    verify(player, times(6)).setVelocity(velocities.capture());
    assertThat(velocities.getAllValues()).containsExactly(
        new Vector(0.1, -0.2, 0), new Vector(0.14, -0.2, 0), new Vector(0.14, -0.2, 0),
        new Vector(0.04, -0.2, 0), new Vector(0, -0.2, 0), new Vector(0.1, -0.2, 0));
  }

  @Test
  void simultaneousUnlearningStopsAnEarlierBoostingSession() throws Exception {
    Constructor<VelocityBurstRuntime> constructor = VelocityBurstRuntime.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    VelocityBurstRuntime runtime = constructor.newInstance();
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    when(player.getVelocity()).thenReturn(new Vector(0.3, -0.2, 0.1));
    Object bucket = bucket(player);
    for (int i = 0; i < 2; i++) {
      VelocityBurstRuntime.Client client = client(runtime, mock(VelocityBurstRuntime.Feedback.class));
      Object session = session(client);
      sessions(client).put(player.getUniqueId(), session);
      ledger(bucket).put(client, session);
    }
    Object first = ledger(bucket).entries().iterator().next().getValue();
    Field boosting = first.getClass().getDeclaredField("boosting");
    boosting.setAccessible(true);
    boosting.setBoolean(first, true);
    Method remove = VelocityBurstRuntime.class.getDeclaredMethod("removeExpiredSessions", bucket.getClass(), Player.class, long.class);
    remove.setAccessible(true);

    remove.invoke(runtime, bucket, player, 1L);

    assertThat(ledger(bucket).isEmpty()).isTrue();
    verify(player).setVelocity(new Vector(0, -0.2, 0));
  }

  private static Object bucket(Player player) throws Exception {
    Class<?> type = Class.forName(VelocityBurstRuntime.class.getName() + "$PlayerBucket");
    Constructor<?> constructor = type.getDeclaredConstructor(UUID.class, Player.class, long.class);
    constructor.setAccessible(true);
    return constructor.newInstance(player.getUniqueId(), player, 0L);
  }

  @SuppressWarnings("unchecked")
  private static VelocityBurstRuntime.SessionLedger<VelocityBurstRuntime.Client, Object> ledger(Object bucket) throws Exception {
    Field field = bucket.getClass().getDeclaredField("sessions");
    field.setAccessible(true);
    return (VelocityBurstRuntime.SessionLedger<VelocityBurstRuntime.Client, Object>) field.get(bucket);
  }

  @Test
  void stoppingOneClientSettlesItsEndCallbackOnceWithoutStoppingAnother() throws Exception {
    Constructor<VelocityBurstRuntime> constructor = VelocityBurstRuntime.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    VelocityBurstRuntime runtime = constructor.newInstance();
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    VelocityBurstRuntime.Feedback firstFeedback = mock(VelocityBurstRuntime.Feedback.class);
    VelocityBurstRuntime.Feedback secondFeedback = mock(VelocityBurstRuntime.Feedback.class);
    VelocityBurstRuntime.Client first = client(runtime, firstFeedback);
    VelocityBurstRuntime.Client second = client(runtime, secondFeedback);
    Object firstSession = session(first);
    Object secondSession = session(second);
    sessions(first).put(player.getUniqueId(), firstSession);
    sessions(second).put(player.getUniqueId(), secondSession);
    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.isOwnedByCurrentRegion(player)).thenReturn(true);
      first.stop(player);
      first.stop(player);
    }
    assertThat(sessions(first)).isEmpty();
    assertThat(sessions(second).get(player.getUniqueId())).isSameAs(secondSession);
    verify(firstFeedback, times(1)).onEnded(player);
    verify(secondFeedback, never()).onEnded(player);
  }

  @Test
  void queuedStopCannotRemoveAReplacementSession() throws Exception {
    Constructor<VelocityBurstRuntime> constructor = VelocityBurstRuntime.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    VelocityBurstRuntime runtime = constructor.newInstance();
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    VelocityBurstRuntime.Feedback feedback = mock(VelocityBurstRuntime.Feedback.class);
    VelocityBurstRuntime.Client client = client(runtime, feedback);
    sessions(client).put(player.getUniqueId(), session(client));
    List<Runnable> scheduled = new ArrayList<>();
    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.isOwnedByCurrentRegion(player)).thenReturn(false);
      scheduling.when(() -> J.runEntity(eq(player), any(Runnable.class))).thenAnswer(invocation -> {
        scheduled.add(invocation.getArgument(1));
        return true;
      });
      client.stop(player);
      Object replacement = session(client);
      sessions(client).put(player.getUniqueId(), replacement);
      scheduling.when(() -> J.isOwnedByCurrentRegion(player)).thenReturn(true);
      assertThat(scheduled).hasSize(1);
      scheduled.getFirst().run();
      assertThat(sessions(client).get(player.getUniqueId())).isSameAs(replacement);
      verify(feedback, never()).onEnded(player);
    }
  }

  @Test
  void unlearnedOwnerEndsAnActiveBurstBeforeItCanAccelerate() throws Exception {
    Constructor<VelocityBurstRuntime> constructor = VelocityBurstRuntime.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    VelocityBurstRuntime runtime = constructor.newInstance();
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    when(player.getVelocity()).thenReturn(new Vector(0.3, -0.2, 0.1));
    VelocityBurstRuntime.Feedback feedback = mock(VelocityBurstRuntime.Feedback.class);
    VelocityBurstRuntime.Client client = client(runtime, feedback);
    Object session = session(client);
    sessions(client).put(player.getUniqueId(), session);
    Class<?> bucketType = Class.forName(VelocityBurstRuntime.class.getName() + "$PlayerBucket");
    Constructor<?> bucketConstructor = bucketType.getDeclaredConstructor(UUID.class, Player.class, long.class);
    bucketConstructor.setAccessible(true);
    Object bucket = bucketConstructor.newInstance(player.getUniqueId(), player, 0L);
    Field ledgerField = bucketType.getDeclaredField("sessions");
    ledgerField.setAccessible(true);
    @SuppressWarnings("unchecked")
    VelocityBurstRuntime.SessionLedger<VelocityBurstRuntime.Client, Object> ledger =
        (VelocityBurstRuntime.SessionLedger<VelocityBurstRuntime.Client, Object>) ledgerField.get(bucket);
    ledger.put(client, session);
    Method removeExpired = VelocityBurstRuntime.class.getDeclaredMethod("removeExpiredSessions", bucketType, Player.class, long.class);
    removeExpired.setAccessible(true);
    Field sourceField = VelocityBurstRuntime.Client.class.getDeclaredField("source");
    sourceField.setAccessible(true);
    Adaptation<?> source = (Adaptation<?>) sourceField.get(client);
    when(source.getActiveLevel(player)).thenReturn(1);
    Field boostingField = session.getClass().getDeclaredField("boosting");
    boostingField.setAccessible(true);
    boostingField.setBoolean(session, true);

    removeExpired.invoke(runtime, bucket, player, 1L);

    assertThat(ledger.isEmpty()).isFalse();
    verify(feedback, never()).onEnded(player);
    when(source.getActiveLevel(player)).thenReturn(0);
    removeExpired.invoke(runtime, bucket, player, 2L);

    assertThat(ledger.isEmpty()).isTrue();
    assertThat(sessions(client)).isEmpty();
    verify(feedback).onEnded(player);
    verify(player).setVelocity(new Vector(0, -0.2, 0));
  }

  private static VelocityBurstRuntime.Client client(VelocityBurstRuntime runtime,
                                                   VelocityBurstRuntime.Feedback feedback) throws Exception {
    Constructor<VelocityBurstRuntime.Client> constructor = VelocityBurstRuntime.Client.class.getDeclaredConstructor(
        VelocityBurstRuntime.class, Adaptation.class, VelocityBurstRuntime.Feedback.class);
    constructor.setAccessible(true);
    return constructor.newInstance(runtime, mock(Adaptation.class), feedback);
  }

  private static Object session(VelocityBurstRuntime.Client client) throws Exception {
    Class<?> type = Class.forName(VelocityBurstRuntime.class.getName() + "$BurstSession");
    Constructor<?> constructor = type.getDeclaredConstructor(VelocityBurstRuntime.Client.class,
        long.class, int.class, VelocityBurstRuntime.Profile.class);
    constructor.setAccessible(true);
    return constructor.newInstance(client, Long.MAX_VALUE, 1,
        new VelocityBurstRuntime.Profile(0.1D, 0.5D, 0.1D, 0.1D, 0.01D, true, 0.01D));
  }

  @SuppressWarnings("unchecked")
  private static Map<UUID, Object> sessions(VelocityBurstRuntime.Client client) throws Exception {
    Field field = VelocityBurstRuntime.Client.class.getDeclaredField("sessions");
    field.setAccessible(true);
    return (Map<UUID, Object>) field.get(client);
  }

  @Test
  void productionScaleHasAnExactGlobalCallbackCeiling() {
    assertThat(VelocityBurstRuntime.boundedOwnerCallbacks(-1)).isZero();
    assertThat(VelocityBurstRuntime.boundedOwnerCallbacks(40)).isEqualTo(40);
    assertThat(VelocityBurstRuntime.boundedOwnerCallbacks(1_000))
        .isEqualTo(VelocityBurstRuntime.MAX_OWNER_CALLBACKS_PER_TICK);
    assertThat(VelocityBurstRuntime.MAX_OWNER_CALLBACKS_PER_SECOND).isEqualTo(6_400);
  }

  @Test
  void elapsedCadenceScalesMovementWithoutUnboundedCatchup() {
    assertThat(VelocityBurstRuntime.elapsedTickScale(-1L)).isEqualTo(1D);
    assertThat(VelocityBurstRuntime.elapsedTickScale(25L)).isEqualTo(1D);
    assertThat(VelocityBurstRuntime.elapsedTickScale(150L)).isEqualTo(3D);
    assertThat(VelocityBurstRuntime.elapsedTickScale(200L)).isEqualTo(4D);
    assertThat(VelocityBurstRuntime.elapsedTickScale(5_000L)).isEqualTo(4D);
  }

  @Test
  void overlapExtendsOnlyAcceptedBursts() {
    VelocityBurstRuntime.StartDecision started = VelocityBurstRuntime.decideStart(
        false, 0L, 0, 1_000L, 250L, 2, false);
    VelocityBurstRuntime.StartDecision rejected = VelocityBurstRuntime.decideStart(
        true, started.expiresAt(), started.amplifier(), 1_100L, 250L, 5, false);
    VelocityBurstRuntime.StartDecision extended = VelocityBurstRuntime.decideStart(
        true, started.expiresAt(), started.amplifier(), 1_100L, 250L, 5, true);

    assertThat(started.result()).isEqualTo(VelocityBurstRuntime.StartResult.STARTED);
    assertThat(started.expiresAt()).isEqualTo(1_250L);
    assertThat(rejected.result()).isEqualTo(VelocityBurstRuntime.StartResult.REJECTED_ACTIVE);
    assertThat(rejected.expiresAt()).isEqualTo(1_250L);
    assertThat(extended.result()).isEqualTo(VelocityBurstRuntime.StartResult.EXTENDED);
    assertThat(extended.expiresAt()).isEqualTo(1_500L);
    assertThat(extended.amplifier()).isEqualTo(5);
  }

  @Test
  void expiredBurstsRestartAndExpiryArithmeticSaturates() {
    VelocityBurstRuntime.StartDecision restarted = VelocityBurstRuntime.decideStart(
        true, 1_000L, 8, 1_000L, 100L, 1, false);
    VelocityBurstRuntime.StartDecision saturated = VelocityBurstRuntime.decideStart(
        true, Long.MAX_VALUE - 20L, 1, 0L, 100L, 2, true);

    assertThat(restarted.result()).isEqualTo(VelocityBurstRuntime.StartResult.STARTED);
    assertThat(restarted.expiresAt()).isEqualTo(1_100L);
    assertThat(restarted.amplifier()).isEqualTo(1);
    assertThat(saturated.expiresAt()).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  void ownerCleanupCannotRemoveAnotherAdaptationsSession() {
    VelocityBurstRuntime.SessionLedger<Object, Object> sessions =
        new VelocityBurstRuntime.SessionLedger<>(VelocityBurstRuntime.MAX_SESSIONS_PER_PLAYER);
    Object hunter = new Object();
    Object bloodPact = new Object();
    Object hunterSession = new Object();
    Object bloodPactSession = new Object();
    Object staleHunterSession = new Object();

    assertThat(sessions.put(hunter, hunterSession)).isTrue();
    assertThat(sessions.put(bloodPact, bloodPactSession)).isTrue();
    assertThat(sessions.remove(hunter, staleHunterSession)).isFalse();
    assertThat(sessions.size()).isEqualTo(2);
    assertThat(sessions.remove(hunter, hunterSession)).isTrue();
    assertThat(sessions.get(bloodPact)).isSameAs(bloodPactSession);
    assertThat(sessions.size()).isEqualTo(1);

    sessions.clear();

    assertThat(sessions.isEmpty()).isTrue();
  }

  @Test
  void perPlayerSessionCapacityIsHardBounded() {
    VelocityBurstRuntime.SessionLedger<Object, Object> sessions =
        new VelocityBurstRuntime.SessionLedger<>(2);
    Object firstOwner = new Object();
    Object secondOwner = new Object();

    assertThat(sessions.put(firstOwner, new Object())).isTrue();
    assertThat(sessions.put(secondOwner, new Object())).isTrue();
    assertThat(sessions.put(new Object(), new Object())).isFalse();
    assertThat(sessions.size()).isEqualTo(2);
    assertThat(sessions.put(firstOwner, new Object())).isTrue();
    assertThat(sessions.size()).isEqualTo(2);
  }
}
