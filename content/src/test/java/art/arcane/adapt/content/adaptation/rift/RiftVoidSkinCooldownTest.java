package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.engine.ability.EngineAbilityCharge;
import art.arcane.adapt.SchedulerForwarding;
import art.arcane.adapt.api.ability.AbilityDefaultCost;
import art.arcane.adapt.api.ability.AbilityOutcome;
import art.arcane.adapt.api.content.DeathSaveSlot;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiftVoidSkinCooldownTest {
  @Test
  void pendingEscapeHasACooldownBeforeTeleportCompletion() throws Exception {
    try (Fixture fixture = new Fixture()) {
      fixture.skin.on(fixture.damage);

      assertThat(fixture.teleport.isDone()).isFalse();
      assertThat(RiftVoidEscape.DEATH_SAVE.isReady(fixture.player, System.currentTimeMillis())).isFalse();
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
      assertThat(RiftVoidEscape.DEATH_SAVE.isReady(fixture.player, System.currentTimeMillis())).isFalse();
      verify(fixture.inventory, times(1)).addItem(any(ItemRef.class));
      assertThat(fixture.saved).doesNotContainKey(fixture.reservationKey.id());
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
      verify(fixture.inventory, times(1)).addItem(any(ItemRef.class));
      EntityDamageEvent nextHit = fixture.damageEvent();
      fixture.skin.on(nextHit);
      verify(nextHit, never()).setCancelled(true);
    }
  }

  @Test
  void failedDeadlineWriteRefundsTheReservationWithoutGrantingSurvival() throws Exception {
    try (Fixture fixture = new Fixture()) {
      IllegalStateException failure = new IllegalStateException("Storage unavailable");
      fixture.data.longFailure = failure;

      fixture.skin.on(fixture.damage);

      verify(fixture.damage, never()).setCancelled(true);
      verify(fixture.inventory).addItem(any(ItemRef.class));
      verify(fixture.player, never()).teleport(any(Loc.class), any(TeleportCause.class));
      assertThat(fixture.adapt.platform().log().errors()).contains(failure);
    }
  }

  @Test
  void staleRefundCannotReturnOrClearANewerPearlReservation() throws Exception {
    try (Fixture fixture = new Fixture()) {
      fixture.skin.on(fixture.damage);
      fixture.saved.put(fixture.reservationKey.id(), "new-reservation");

      fixture.teleport.complete(false);

      verify(fixture.inventory, never()).addItem(any(ItemRef.class));
      assertThat(fixture.saved).containsEntry(fixture.reservationKey.id(), "new-reservation");
      assertThat(RiftVoidEscape.DEATH_SAVE.isReady(fixture.player, System.currentTimeMillis())).isFalse();
    }
  }

  private static final class Fixture implements AutoCloseable {
    private final Path dataDirectory = Files.createTempDirectory("rift-void_skin");
    private final TestAdapt adapt = TestAdapt.start(TestPlatform.builder(dataDirectory).build(), List.of());
    private final RiftVoidSkin skin = mock(RiftVoidSkin.class, CALLS_REAL_METHODS);
    private final PlayerRef player = mock(PlayerRef.class);
    private final PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
    private final Map<Id, Object> saved = new HashMap<>();
    private final MapData data = new MapData(saved);
    private final DataKey<String> reservationKey = DataKey.adapt("rift_void_skin_reserved_pearl", DataCodecs.STRING);
    private final CompletableFuture<Boolean> teleport = new CompletableFuture<>();
    private final MockedStatic<RiftPearls> pearls = mockStatic(RiftPearls.class, CALLS_REAL_METHODS);
    private final MockedStatic<Tasks> ownership = SchedulerForwarding.mockTasks(CALLS_REAL_METHODS);
    private final EntityDamageEvent damage;

    private Fixture() throws Exception {
      assign("escape", new RiftVoidEscape(skin, (PlayerRef ignored) -> {
      }));
      doReturn(new RiftVoidSkin.Config()).when(skin).getConfig();
      doReturn(1).when(skin).getActiveLevel(player);
      doReturn(RiftVoidSkin.Causes.ALL).when(skin).preference(player, RiftVoidSkin.CAUSES);
      doReturn(RiftVoidSkin.Reserve.NONE).when(skin).preference(player, RiftVoidSkin.RESERVE);
      doReturn(true).when(skin).preferenceEnabled(player, RiftVoidSkin.SPAWN);
      when(player.uuid()).thenReturn(UUID.randomUUID());
      when(player.health()).thenReturn(4D);
      when(player.isOnline()).thenReturn(true);
      when(player.isValid()).thenReturn(true);
      when(player.inventory()).thenReturn(inventory);
      when(player.data()).thenReturn(data);
      WorldRef world = mock(WorldRef.class);
      when(player.world()).thenReturn(world);
      when(player.location()).thenAnswer(call -> new Loc(world, 0D, 80D, 0D));
      when(world.spawnLocation()).thenReturn(new Loc(world, 20D, 80D, 20D));
      ItemRef pearl = mock(ItemRef.class);
      when(pearl.amount()).thenReturn(1);
      when(pearl.copyWithAmount(anyInt())).thenReturn(pearl);
      when(inventory.contents()).thenReturn(List.of(pearl));
      when(inventory.addItem(any(ItemRef.class))).thenReturn(List.of());
      pearls.when(() -> RiftPearls.isPlainPearl(pearl)).thenReturn(true);
      ownership.when(() -> Tasks.isOwnedByCurrentRegion(any(Loc.class))).thenReturn(false);
      doAnswer(call -> {
        AbilityDefaultCost cost = call.getArgument(4);
        assertThat(cost.take()).isTrue();
        return new EngineAbilityCharge(UUID.randomUUID(), AbilityOutcome.ALLOWED_DEFAULT, false, "", "", List.of());
      }).when(skin).payItemCostDeferred(eq(player), eq("pearl"), any(ItemRef.class), anyInt(), any(AbilityDefaultCost.class));
      doReturn(true).when(skin).refundCost(any(UUID.class), any());
      when(player.teleport(any(Loc.class), eq(TeleportCause.PLUGIN))).thenReturn(teleport);
      damage = damageEvent();
    }

    private long deadline() {
      return (Long) saved.get(Id.adapt("death_save_rift_void_skin"));
    }

    private EntityDamageEvent damageEvent() {
      return spy(new EntityDamageEvent(player, DamageCause.CUSTOM, Id.minecraft("generic"), 10D, FinalDamage.IDENTITY));
    }

    private void quit() {
      skin.on(new PlayerQuitEvent(player));
    }

    private void assign(String name, Object value) throws Exception {
      Field field = RiftVoidSkin.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(skin, value);
    }

    @Override
    public void close() {
      ownership.close();
      pearls.close();
      adapt.close();
    }
  }

  private static final class MapData implements DataHolder {
    private final Map<Id, Object> values;
    private RuntimeException longFailure;

    private MapData(Map<Id, Object> values) {
      this.values = values;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(DataKey<T> key) {
      return (T) values.get(key.id());
    }

    @Override
    public boolean has(DataKey<?> key) {
      return values.containsKey(key.id());
    }

    @Override
    public boolean hasAny(Id id) {
      return values.containsKey(id);
    }

    @Override
    public <T> void set(DataKey<T> key, T value) {
      if (longFailure != null && value instanceof Long) {
        throw longFailure;
      }
      values.put(key.id(), value);
    }

    @Override
    public void remove(Id id) {
      values.remove(id);
    }

    @Override
    public Set<Id> keys() {
      return values.keySet();
    }

    @Override
    public boolean isEmpty() {
      return values.isEmpty();
    }
  }
}
