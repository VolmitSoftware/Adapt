package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.content.adaptation.rift.RiftBlink;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeleportUsageXpTest {
  private final WorldRef world = mock(WorldRef.class);

  @ParameterizedTest
  @CsvSource({"true, true", "false, true", "true, false", "false, false"})
  void blinkRewardsOnlyTheCompletedOperationAndNeverADuplicateCallback(boolean success, boolean online) throws Exception {
    RiftBlink blink = mock(RiftBlink.class, RETURNS_DEEP_STUBS);
    PlayerRef player = player(online);
    when(blink.getLevel(player)).thenReturn(2);
    UUID ticket = UUID.randomUUID();
    Map<UUID, UUID> pending = new HashMap<>();
    pending.put(player.uuid(), ticket);
    Field pendingField = RiftBlink.class.getDeclaredField("pendingBlinks");
    pendingField.setAccessible(true);
    pendingField.set(blink, pending);
    Field cooldownField = RiftBlink.class.getDeclaredField("lastBlink");
    cooldownField.setAccessible(true);
    cooldownField.set(blink, new Cooldowns(new HashMap<>(), null));
    Class<?> operationType = Class.forName(RiftBlink.class.getName() + "$BlinkOperation");
    Class<?> attackType = Class.forName(RiftBlink.class.getName() + "$PendingAttack");
    Constructor<?> constructor = operationType.getDeclaredConstructor(UUID.class, UUID.class, Loc.class, Loc.class, Vec3.class, double.class, attackType);
    constructor.setAccessible(true);
    Loc origin = player.location();
    Object operation = constructor.newInstance(player.uuid(), ticket, origin, origin, Vec3.ZERO, 0D, null);
    Method finish = RiftBlink.class.getDeclaredMethod("finishBlink", PlayerRef.class, operationType, Boolean.class, Throwable.class);
    finish.setAccessible(true);
    try (MockedStatic<Tasks> scheduler = immediateScheduler(player)) {
      finish.invoke(blink, player, operation, success, null);
      finish.invoke(blink, player, operation, success, null);
    }
    verify(blink, times(success && online ? 1 : 0)).awardUsageBaselineXp(player, 2);
  }

  private PlayerRef player(boolean online) {
    PlayerRef player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(UUID.randomUUID());
    when(player.isOnline()).thenReturn(online);
    Loc location = new Loc(world, 0D, 70D, 0D);
    when(player.location()).thenAnswer(invocation -> location);
    return player;
  }

  private MockedStatic<Tasks> immediateScheduler(PlayerRef player) {
    MockedStatic<Tasks> scheduler = mockStatic(Tasks.class);
    scheduler.when(() -> Tasks.runEntity(eq(player), any(Runnable.class))).thenAnswer(invocation -> {
      Runnable callback = invocation.getArgument(1);
      callback.run();
      return true;
    });
    return scheduler;
  }
}
