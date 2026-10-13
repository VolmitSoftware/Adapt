package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityDefaultCost;
import art.arcane.adapt.api.ability.AbilityOutcome;
import art.arcane.adapt.api.adaptation.DeathSaveCooldown;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.MockedConstruction;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiftVoidSkinCooldownTest {
  @Test
  void pendingEscapeHasACooldownBeforeTeleportCompletion() throws Exception {
    try (Fixture fixture = new Fixture()) {
      fixture.skin.on(fixture.damage);

      assertThat(fixture.teleport.isDone()).isFalse();
      assertThat(DeathSaveCooldown.VOID_SKIN.isReady(fixture.player, System.currentTimeMillis())).isFalse();
      verify(fixture.damage).setCancelled(true);
    }
  }

  @Test
  void failedTeleportRefundsExactlyOnceWithoutRestoringTheSave() throws Exception {
    try (Fixture fixture = new Fixture()) {
      fixture.skin.on(fixture.damage);
      long deadline = fixture.deadline();

      fixture.teleport.complete(false);
      fixture.quit();

      assertThat(fixture.deadline()).isEqualTo(deadline);
      assertThat(DeathSaveCooldown.VOID_SKIN.isReady(fixture.player, System.currentTimeMillis())).isFalse();
      verify(fixture.inventory, times(1)).addItem(any(ItemStack.class));
      assertThat(fixture.saved).doesNotContainKey(fixture.reservationKey);
    }
  }

  @Test
  void quitDuringPendingEscapeKeepsTheDeadlineAndLateCompletionCannotRefundAgain() throws Exception {
    try (Fixture fixture = new Fixture()) {
      fixture.skin.on(fixture.damage);
      long deadline = fixture.deadline();

      fixture.quit();
      fixture.teleport.complete(false);

      assertThat(fixture.deadline()).isEqualTo(deadline);
      verify(fixture.inventory, times(1)).addItem(any(ItemStack.class));
      EntityDamageEvent nextHit = fixture.damageEvent();
      fixture.skin.on(nextHit);
      verify(nextHit, never()).setCancelled(true);
    }
  }

  @Test
  void failedDeadlineWriteRefundsTheReservationWithoutGrantingSurvival() throws Exception {
    try (Fixture fixture = new Fixture()) {
      IllegalStateException failure = new IllegalStateException("Storage unavailable");
      doThrow(failure).when(fixture.data).set(any(), eq(PersistentDataType.LONG), anyLong());

      fixture.skin.on(fixture.damage);

      verify(fixture.damage, never()).setCancelled(true);
      verify(fixture.inventory).addItem(any(ItemStack.class));
      fixture.teleports.verifyNoInteractions();
      fixture.logging.verify(() -> Adapt.error(failure));
    }
  }

  @Test
  void staleRefundCannotReturnOrClearANewerPearlReservation() throws Exception {
    try (Fixture fixture = new Fixture()) {
      fixture.skin.on(fixture.damage);
      fixture.saved.put(fixture.reservationKey, "new-reservation");

      fixture.teleport.complete(false);

      verify(fixture.inventory, never()).addItem(any(ItemStack.class));
      assertThat(fixture.saved).containsEntry(fixture.reservationKey, "new-reservation");
      assertThat(DeathSaveCooldown.VOID_SKIN.isReady(fixture.player, System.currentTimeMillis())).isFalse();
    }
  }

  private static final class Fixture implements AutoCloseable {
    private final RiftVoidSkin skin = mock(RiftVoidSkin.class, CALLS_REAL_METHODS);
    private final Player player = mock(Player.class);
    private final PlayerInventory inventory = mock(PlayerInventory.class);
    private final PersistentDataContainer data = mock(PersistentDataContainer.class);
    private final Map<NamespacedKey, Object> saved = new HashMap<>();
    private final NamespacedKey reservationKey = new NamespacedKey("adapt", "rift_void_skin_reserved_pearl");
    private final CompletableFuture<Boolean> teleport = new CompletableFuture<>();
    private final MockedStatic<J> scheduler = mockStatic(J.class);
    private final MockedStatic<PaperCompat> teleports = mockStatic(PaperCompat.class);
    private final MockedStatic<RiftPearls> pearls = mockStatic(RiftPearls.class);
    private final MockedStatic<Adapt> logging = mockStatic(Adapt.class);
    private final MockedConstruction<ItemStack> items = mockConstruction(ItemStack.class);
    private final EntityDamageEvent damage;

    private Fixture() throws Exception {
      assign("acceptingEscapes", new AtomicBoolean(true));
      assign("pendingEscapes", new ConcurrentHashMap<>());
      assign("pearlReservationKey", reservationKey);
      doReturn(new RiftVoidSkin.Config()).when(skin).getConfig();
      doReturn(1).when(skin).getActiveLevel(player);
      doReturn(RiftVoidSkin.Causes.ALL).when(skin).preference(player, RiftVoidSkin.CAUSES);
      doReturn(RiftVoidSkin.Reserve.NONE).when(skin).preference(player, RiftVoidSkin.RESERVE);
      doReturn(true).when(skin).preferenceEnabled(player, RiftVoidSkin.SPAWN);
      when(player.getUniqueId()).thenReturn(UUID.randomUUID());
      when(player.getHealth()).thenReturn(4D);
      when(player.isOnline()).thenReturn(true);
      when(player.getInventory()).thenReturn(inventory);
      when(player.getPersistentDataContainer()).thenReturn(data);
      World world = mock(World.class);
      when(player.getWorld()).thenReturn(world);
      when(player.getLocation()).thenAnswer(call -> new Location(world, 0D, 80D, 0D));
      when(world.getSpawnLocation()).thenReturn(new Location(world, 20D, 80D, 20D));
      ItemStack pearl = mock(ItemStack.class);
      when(pearl.getAmount()).thenReturn(1);
      when(inventory.getContents()).thenReturn(new ItemStack[]{pearl});
      when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
      pearls.when(() -> RiftPearls.isPlainPearl(pearl)).thenReturn(true);
      when(data.getOrDefault(any(), eq(PersistentDataType.LONG), anyLong()))
          .thenAnswer(call -> saved.getOrDefault(call.getArgument(0), call.getArgument(2)));
      when(data.get(any(), eq(PersistentDataType.STRING)))
          .thenAnswer(call -> saved.get(call.getArgument(0)));
      doAnswer(call -> {
        saved.put(call.getArgument(0), call.getArgument(2));
        return null;
      }).when(data).set(any(), any(), any());
      doAnswer(call -> {
        saved.remove(call.getArgument(0));
        return null;
      }).when(data).remove(any());
      doAnswer(call -> {
        AbilityDefaultCost cost = call.getArgument(4);
        assertThat(cost.take()).isTrue();
        return new AbilityCharge(UUID.randomUUID(), AbilityOutcome.ALLOWED_DEFAULT, false, "", "", List.of());
      }).when(skin).payItemCostDeferred(eq(player), eq("pearl"), any(ItemStack.class), anyInt(), any(AbilityDefaultCost.class));
      doReturn(true).when(skin).refundCost(any(UUID.class), any());
      scheduler.when(() -> J.runEntity(eq(player), any(Runnable.class))).thenAnswer(call -> {
        Runnable callback = call.getArgument(1);
        callback.run();
        return true;
      });
      teleports.when(() -> PaperCompat.teleportAsync(eq(player), any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN)))
          .thenReturn(teleport);
      damage = damageEvent();
    }

    private long deadline() {
      return (Long) saved.get(new NamespacedKey("adapt", "death_save_rift_void_skin"));
    }

    private EntityDamageEvent damageEvent() {
      EntityDamageEvent event = mock(EntityDamageEvent.class);
      when(event.getEntity()).thenReturn(player);
      when(event.getFinalDamage()).thenReturn(10D);
      return event;
    }

    private void quit() {
      PlayerQuitEvent event = mock(PlayerQuitEvent.class);
      when(event.getPlayer()).thenReturn(player);
      skin.on(event);
    }

    private void assign(String name, Object value) throws Exception {
      Field field = RiftVoidSkin.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(skin, value);
    }

    @Override
    public void close() {
      items.close();
      logging.close();
      pearls.close();
      teleports.close();
      scheduler.close();
    }
  }
}
