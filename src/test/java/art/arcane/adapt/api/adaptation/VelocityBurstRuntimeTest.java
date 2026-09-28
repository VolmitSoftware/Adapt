package art.arcane.adapt.api.adaptation;

import org.junit.jupiter.api.Test;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.entity.Player;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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

  private static VelocityBurstRuntime.Client client(VelocityBurstRuntime runtime,
                                                   VelocityBurstRuntime.Feedback feedback) throws Exception {
    Constructor<VelocityBurstRuntime.Client> constructor = VelocityBurstRuntime.Client.class.getDeclaredConstructor(
        VelocityBurstRuntime.class, String.class, VelocityBurstRuntime.Feedback.class);
    constructor.setAccessible(true);
    return constructor.newInstance(runtime, "test", feedback);
  }

  private static Object session(VelocityBurstRuntime.Client client) throws Exception {
    Class<?> type = Class.forName(VelocityBurstRuntime.class.getName() + "$BurstSession");
    Constructor<?> constructor = type.getDeclaredConstructor(VelocityBurstRuntime.Client.class,
        long.class, int.class, VelocityBurstRuntime.Profile.class);
    constructor.setAccessible(true);
    return constructor.newInstance(client, Long.MAX_VALUE, 1,
        new VelocityBurstRuntime.Profile(0.1D, 0.5D, 0.1D, 0.1D, 0.01D, false, 0.01D));
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
