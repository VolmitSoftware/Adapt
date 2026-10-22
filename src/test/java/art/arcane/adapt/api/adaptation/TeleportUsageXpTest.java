package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.content.adaptation.architect.ArchitectElevator;
import art.arcane.adapt.content.adaptation.rift.RiftBlink;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.adapt.util.reflect.registries.RegistryUtil;
import io.papermc.paper.registry.RegistryAccess;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeleportUsageXpTest extends AdaptTestBase {
  private final World world = mock(World.class);

  @BeforeAll
  static void initializeEffects() {
    RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
    try (MockedStatic<RegistryAccess> registryAccess = mockStatic(RegistryAccess.class);
         MockedStatic<RegistryUtil> registries = mockStatic(RegistryUtil.class)) {
      registryAccess.when(RegistryAccess::registryAccess).thenReturn(access);
      Registry<Sound> sounds = Registry.SOUNDS;
      doAnswer(invocation -> mock(Sound.class)).when(sounds).getOrThrow(any(Key.class));
      registries.when(() -> RegistryUtil.find(eq(Particle.class), any(String[].class))).thenAnswer(invocation -> {
        String[] keys = (String[]) invocation.getRawArguments()[1];
        for (String key : keys) {
          for (Particle particle : Particle.values()) {
            if (particle.getKey().getKey().equals(key)) {
              return particle;
            }
          }
        }
        throw new IllegalArgumentException("Unknown particle");
      });
      assertNotNull(Sound.ENTITY_ENDERMAN_TELEPORT);
      assertNotNull(Particles.END_ROD);
    }
  }

  @ParameterizedTest
  @CsvSource({"true, true", "false, true", "true, false", "false, false"})
  void elevatorRewardsOnlyCompletedTeleportsForOnlinePlayers(boolean success, boolean online) throws Exception {
    when(plugin.namespace()).thenReturn("adapt");
    ArchitectElevator elevator = mock(ArchitectElevator.class, RETURNS_DEEP_STUBS);
    Player player = player(online);
    when(elevator.getLevel(player)).thenReturn(1);
    Method finish = ArchitectElevator.class.getDeclaredMethod("finishElevatorTeleport", Player.class, int.class, Boolean.class, Throwable.class);
    finish.setAccessible(true);
    try (MockedStatic<J> scheduler = immediateScheduler(player)) {
      finish.invoke(elevator, player, 5, success, null);
    }
    verify(elevator, times(success && online ? 1 : 0)).awardUsageBaselineXp(player, 1);
  }

  @ParameterizedTest
  @CsvSource({"true, true", "false, true", "true, false", "false, false"})
  void blinkRewardsOnlyTheCompletedOperationAndNeverADuplicateCallback(boolean success, boolean online) throws Exception {
    RiftBlink blink = mock(RiftBlink.class, RETURNS_DEEP_STUBS);
    Player player = player(online);
    when(blink.getLevel(player)).thenReturn(2);
    UUID ticket = UUID.randomUUID();
    Map<UUID, UUID> pending = new HashMap<>();
    pending.put(player.getUniqueId(), ticket);
    Field pendingField = RiftBlink.class.getDeclaredField("pendingBlinks");
    pendingField.setAccessible(true);
    pendingField.set(blink, pending);
    Field cooldownField = RiftBlink.class.getDeclaredField("lastBlink");
    cooldownField.setAccessible(true);
    cooldownField.set(blink, new Cooldowns(new HashMap<>()));
    Class<?> operationType = Class.forName(RiftBlink.class.getName() + "$BlinkOperation");
    Class<?> attackType = Class.forName(RiftBlink.class.getName() + "$PendingAttack");
    Constructor<?> constructor = operationType.getDeclaredConstructor(UUID.class, UUID.class, Location.class, Location.class, Vector.class, double.class, attackType);
    constructor.setAccessible(true);
    Location origin = player.getLocation();
    Object operation = constructor.newInstance(player.getUniqueId(), ticket, origin, origin, new Vector(), 0D, null);
    Method finish = RiftBlink.class.getDeclaredMethod("finishBlink", Player.class, operationType, Boolean.class, Throwable.class);
    finish.setAccessible(true);
    try (MockedStatic<J> scheduler = immediateScheduler(player)) {
      finish.invoke(blink, player, operation, success, null);
      finish.invoke(blink, player, operation, success, null);
    }
    verify(blink, times(success && online ? 1 : 0)).awardUsageBaselineXp(player, 2);
  }

  private Player player(boolean online) {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    when(player.isOnline()).thenReturn(online);
    Location location = new Location(world, 0D, 70D, 0D);
    when(player.getLocation()).thenAnswer(invocation -> location.clone());
    return player;
  }

  private MockedStatic<J> immediateScheduler(Player player) {
    MockedStatic<J> scheduler = mockStatic(J.class);
    scheduler.when(() -> J.runEntity(eq(player), any(Runnable.class))).thenAnswer(invocation -> {
      Runnable callback = invocation.getArgument(1);
      callback.run();
      return true;
    });
    return scheduler;
  }
}
