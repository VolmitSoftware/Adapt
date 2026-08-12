package art.arcane.adapt.util.common.plugin;

import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProtectionEventProbeTest {
  private static final Path PROBE_SOURCE =
      Path.of("src/main/java/art/arcane/adapt/util/common/plugin/ProtectionEventProbe.java");

  @Test
  void paperAttemptPickupEventRemainsAnOptionalRuntimeCapability() throws Exception {
    String source = Files.readString(PROBE_SOURCE);

    assertThat(source)
        .contains("Class.forName(ATTEMPT_PICKUP_EVENT)")
        .doesNotContain("import org.bukkit.event.player.PlayerAttemptPickupItemEvent");
  }

  @Test
  void blockMutationProbesDispatchStandardEventsAndHonorDenial() {
    Player player = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    ItemStack held = stubStack(Material.STONE, 1);
    World world = mock(World.class);
    Block block = mock(Block.class);
    BlockState state = mock(BlockState.class);
    Location location = mock(Location.class);
    PluginManager pluginManager = mock(PluginManager.class);
    List<Class<? extends Event>> eventTypes = new ArrayList<>();
    when(player.isOnline()).thenReturn(true);
    when(player.getWorld()).thenReturn(world);
    when(player.getInventory()).thenReturn(inventory);
    when(inventory.getItemInMainHand()).thenReturn(held);
    when(block.getWorld()).thenReturn(world);
    when(block.getLocation()).thenReturn(location);
    when(block.getState()).thenReturn(state);
    doAnswer(invocation -> {
      Event event = invocation.getArgument(0);
      assertThat(ProtectionEventProbe.isActive(event)).isTrue();
      eventTypes.add(event.getClass());
      if (event instanceof BlockBreakEvent blockBreakEvent) {
        blockBreakEvent.setCancelled(true);
      } else if (event instanceof BlockPlaceEvent blockPlaceEvent) {
        blockPlaceEvent.setBuild(false);
      }
      return null;
    }).when(pluginManager).callEvent(any(Event.class));

    boolean breakAllowed;
    boolean placeAllowed;
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
      breakAllowed = ProtectionEventProbe.attemptBlockBreakProbe(player, block);
      placeAllowed = ProtectionEventProbe.attemptBlockPlaceProbe(player, block);
    }

    assertThat(breakAllowed).isFalse();
    assertThat(placeAllowed).isFalse();
    assertThat(eventTypes).containsExactly(BlockBreakEvent.class, BlockPlaceEvent.class);
  }

  @Test
  void crossWorldSyntheticBlockUseRemainsAvailableOffFolia() {
    Player player = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    World playerWorld = mock(World.class);
    World blockWorld = mock(World.class);
    Block block = mock(Block.class);
    PluginManager pluginManager = mock(PluginManager.class);
    when(player.isOnline()).thenReturn(true);
    when(player.getWorld()).thenReturn(playerWorld);
    when(player.getInventory()).thenReturn(inventory);
    when(block.getWorld()).thenReturn(blockWorld);

    boolean allowed;
    try (MockedStatic<J> scheduling = mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
      allowed = ProtectionEventProbe.attemptBlockUse(player, block);
    }

    assertThat(allowed).isTrue();
    verify(pluginManager).callEvent(any(PlayerInteractEvent.class));
  }

  @Test
  void nativeBlockBreakStillRejectsACrossWorldTarget() {
    Player player = mock(Player.class);
    World playerWorld = mock(World.class);
    World blockWorld = mock(World.class);
    Block block = mock(Block.class);
    when(player.isOnline()).thenReturn(true);
    when(player.getWorld()).thenReturn(playerWorld);
    when(block.getWorld()).thenReturn(blockWorld);

    boolean allowed;
    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      allowed = ProtectionEventProbe.attemptBlockBreak(player, block);
    }

    assertThat(allowed).isFalse();
    verify(player, never()).breakBlock(block);
  }

  @Test
  void physicalMutationProbesRejectACrossWorldTarget() {
    Player player = mock(Player.class);
    World playerWorld = mock(World.class);
    World blockWorld = mock(World.class);
    Block block = mock(Block.class);
    PluginManager pluginManager = mock(PluginManager.class);
    when(player.isOnline()).thenReturn(true);
    when(player.getWorld()).thenReturn(playerWorld);
    when(block.getWorld()).thenReturn(blockWorld);

    boolean breakAllowed;
    boolean placeAllowed;
    try (MockedStatic<J> scheduling = mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
      breakAllowed = ProtectionEventProbe.attemptBlockBreakProbe(player, block);
      placeAllowed = ProtectionEventProbe.attemptBlockPlaceProbe(player, block);
    }

    assertThat(breakAllowed).isFalse();
    assertThat(placeAllowed).isFalse();
    verify(pluginManager, never()).callEvent(any(Event.class));
  }

  @Test
  void itemPickupDispatchesThePaperPickupEventSequence() {
    Player player = mock(Player.class);
    Item item = mock(Item.class);
    PluginManager pluginManager = mock(PluginManager.class);
    List<Class<? extends Event>> eventTypes = new ArrayList<>();
    List<Integer> remainingAmounts = new ArrayList<>();
    List<Integer> visibleAmounts = new ArrayList<>();
    when(player.getCanPickupItems()).thenReturn(true);
    AtomicReference<ItemStack> itemStack = stubEligiblePickup(player, item, 8);
    doAnswer(invocation -> {
      Event event = invocation.getArgument(0);
      assertThat(ProtectionEventProbe.isActive(event)).isTrue();
      eventTypes.add(event.getClass());
      visibleAmounts.add(item.getItemStack().getAmount());
      if (event instanceof PlayerAttemptPickupItemEvent attemptEvent) {
        remainingAmounts.add(attemptEvent.getRemaining());
      } else if (event instanceof PlayerPickupItemEvent playerEvent) {
        remainingAmounts.add(playerEvent.getRemaining());
      } else if (event instanceof EntityPickupItemEvent entityEvent) {
        remainingAmounts.add(entityEvent.getRemaining());
      }
      return null;
    }).when(pluginManager).callEvent(any(Event.class));

    boolean allowed;
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
      allowed = ProtectionEventProbe.attemptItemPickup(player, item, 7);
    }

    assertThat(allowed).isTrue();
    assertThat(eventTypes).containsExactly(
        PlayerAttemptPickupItemEvent.class,
        PlayerPickupItemEvent.class,
        EntityPickupItemEvent.class
    );
    assertThat(remainingAmounts).containsExactly(7, 7, 7);
    assertThat(visibleAmounts).containsExactly(8, 1, 1);
    assertThat(itemStack.get().getAmount()).isEqualTo(8);
  }

  @Test
  void itemPickupStopsAtEveryCancelledStage() {
    List<Class<? extends Event>> stages = List.of(
        PlayerAttemptPickupItemEvent.class,
        PlayerPickupItemEvent.class,
        EntityPickupItemEvent.class
    );

    for (Class<? extends Event> deniedStage : stages) {
      Player player = mock(Player.class);
      Item item = mock(Item.class);
      PluginManager pluginManager = mock(PluginManager.class);
      List<Class<? extends Event>> dispatched = new ArrayList<>();
      when(player.getCanPickupItems()).thenReturn(true);
      stubEligiblePickup(player, item, 8);
      doAnswer(invocation -> {
        Event event = invocation.getArgument(0);
        dispatched.add(event.getClass());
        if (deniedStage.isInstance(event)) {
          ((Cancellable) event).setCancelled(true);
        }
        return null;
      }).when(pluginManager).callEvent(any(Event.class));

      boolean allowed;
      try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
        bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
        allowed = ProtectionEventProbe.attemptItemPickup(player, item, -3);
      }

      assertThat(allowed).as(deniedStage.getSimpleName()).isFalse();
      assertThat(dispatched).as(deniedStage.getSimpleName())
          .containsExactlyElementsOf(stages.subList(0, stages.indexOf(deniedStage) + 1));
    }
  }

  @Test
  void itemPickupHonorsPlayersWithPickupDisabled() {
    Player player = mock(Player.class);
    Item item = mock(Item.class);
    PluginManager pluginManager = mock(PluginManager.class);
    List<Class<? extends Event>> dispatched = new ArrayList<>();
    when(player.getCanPickupItems()).thenReturn(false);
    stubEligiblePickup(player, item, 1);
    doAnswer(invocation -> {
      Event event = invocation.getArgument(0);
      dispatched.add(event.getClass());
      return null;
    }).when(pluginManager).callEvent(any(Event.class));

    boolean allowed;
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
      allowed = ProtectionEventProbe.attemptItemPickup(player, item, 0);
    }

    assertThat(allowed).isFalse();
    assertThat(dispatched).containsExactly(
        PlayerAttemptPickupItemEvent.class,
        PlayerPickupItemEvent.class
    );
  }

  @Test
  void itemPickupWithNoCapacityOnlyDispatchesTheAttemptEvent() {
    Player player = mock(Player.class);
    Item item = mock(Item.class);
    PluginManager pluginManager = mock(PluginManager.class);
    List<Class<? extends Event>> dispatched = new ArrayList<>();
    stubEligiblePickup(player, item, 8);
    doAnswer(invocation -> {
      Event event = invocation.getArgument(0);
      dispatched.add(event.getClass());
      return null;
    }).when(pluginManager).callEvent(any(Event.class));

    boolean allowed;
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
      allowed = ProtectionEventProbe.attemptItemPickup(player, item, 8);
    }

    assertThat(allowed).isFalse();
    assertThat(dispatched).containsExactly(PlayerAttemptPickupItemEvent.class);
  }

  @Test
  void pendingBlockDropUsesPickupEventsBeforeTheEntityIsSpawned() {
    Player player = mock(Player.class);
    Item item = mock(Item.class);
    World world = mock(World.class);
    Location source = mock(Location.class);
    PluginManager pluginManager = mock(PluginManager.class);
    List<Class<? extends Event>> dispatched = new ArrayList<>();
    AtomicReference<ItemStack> itemStack = new AtomicReference<>(stubStack(Material.STONE, 8));
    when(player.isOnline()).thenReturn(true);
    when(player.getWorld()).thenReturn(world);
    when(player.getCanPickupItems()).thenReturn(true);
    when(item.isValid()).thenReturn(false);
    when(item.getWorld()).thenReturn(world);
    when(item.getItemStack()).thenAnswer(invocation -> itemStack.get());
    when(source.getWorld()).thenReturn(world);
    doAnswer(invocation -> {
      ItemStack replacement = invocation.getArgument(0);
      itemStack.set(replacement.clone());
      return null;
    }).when(item).setItemStack(any(ItemStack.class));
    doAnswer(invocation -> {
      Event event = invocation.getArgument(0);
      dispatched.add(event.getClass());
      return null;
    }).when(pluginManager).callEvent(any(Event.class));

    boolean allowed;
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
      allowed = ProtectionEventProbe.attemptBlockDropPickup(player, item, 4, source);
    }

    assertThat(allowed).isTrue();
    assertThat(dispatched).containsExactly(
        PlayerAttemptPickupItemEvent.class,
        PlayerPickupItemEvent.class,
        EntityPickupItemEvent.class
    );
    assertThat(itemStack.get().getAmount()).isEqualTo(8);
  }

  @Test
  void livePickupRejectsAnUnspawnedItemWithoutDispatching() {
    Player player = mock(Player.class);
    Item item = mock(Item.class);
    World world = mock(World.class);
    PluginManager pluginManager = mock(PluginManager.class);
    when(player.isOnline()).thenReturn(true);
    when(player.getWorld()).thenReturn(world);
    when(item.getWorld()).thenReturn(world);
    when(item.isValid()).thenReturn(false);

    boolean allowed;
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
      allowed = ProtectionEventProbe.attemptItemPickup(player, item, 0);
    }

    assertThat(allowed).isFalse();
    verify(pluginManager, never()).callEvent(any(Event.class));
  }

  @Test
  void dispatchAlwaysClearsTheProbeMarker() {
    Player player = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    Block block = mock(Block.class);
    World world = mock(World.class);
    PluginManager pluginManager = mock(PluginManager.class);
    AtomicReference<Event> dispatched = new AtomicReference<>();
    when(player.isOnline()).thenReturn(true);
    when(player.getWorld()).thenReturn(world);
    when(block.getWorld()).thenReturn(world);
    when(player.getInventory()).thenReturn(inventory);
    doAnswer(invocation -> {
      Event event = invocation.getArgument(0);
      dispatched.set(event);
      assertThat(ProtectionEventProbe.isActive(event)).isTrue();
      throw new IllegalStateException("listener failure");
    }).when(pluginManager).callEvent(any(Event.class));

    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
      assertThatThrownBy(() -> ProtectionEventProbe.attemptContainerOpen(player, List.of(block)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("listener failure");
    }

    assertThat(dispatched.get()).isExactlyInstanceOf(PlayerInteractEvent.class);
    assertThat(ProtectionEventProbe.isActive(dispatched.get())).isFalse();
  }

  @Test
  void remainingAfterPickupCountsMergesAndEmptyStorageSlots() {
    Inventory inventory = mock(Inventory.class);
    ItemStack requested = stubStack(Material.STONE, 120);
    ItemStack partial = stubStack(Material.STONE, 32);
    ItemStack full = stubStack(Material.DIRT, 64);
    when(inventory.getMaxStackSize()).thenReturn(64);
    when(inventory.getStorageContents()).thenReturn(new ItemStack[]{
        partial,
        full,
        null
    });

    assertThat(ProtectionEventProbe.remainingAfterPickup(inventory, requested)).isEqualTo(24);
  }

  private AtomicReference<ItemStack> stubEligiblePickup(Player player, Item item, int amount) {
    World world = mock(World.class);
    AtomicReference<ItemStack> stack = new AtomicReference<>(stubStack(Material.STONE, amount));
    when(player.isOnline()).thenReturn(true);
    when(player.getWorld()).thenReturn(world);
    when(item.isValid()).thenReturn(true);
    when(item.getWorld()).thenReturn(world);
    when(item.getItemStack()).thenAnswer(invocation -> stack.get());
    doAnswer(invocation -> {
      ItemStack replacement = invocation.getArgument(0);
      stack.set(replacement.clone());
      return null;
    }).when(item).setItemStack(any(ItemStack.class));
    return stack;
  }

  private ItemStack stubStack(Material material, int amount) {
    ItemStack stack = mock(ItemStack.class);
    AtomicInteger currentAmount = new AtomicInteger(amount);
    when(stack.getType()).thenReturn(material);
    when(stack.getAmount()).thenAnswer(invocation -> currentAmount.get());
    when(stack.getMaxStackSize()).thenReturn(64);
    when(stack.isSimilar(any(ItemStack.class))).thenAnswer(invocation -> {
      ItemStack other = invocation.getArgument(0);
      return other != null && other.getType() == material;
    });
    when(stack.clone()).thenAnswer(invocation -> stubStack(material, currentAmount.get()));
    doAnswer(invocation -> {
      currentAmount.set(invocation.getArgument(0));
      return null;
    }).when(stack).setAmount(anyInt());
    return stack;
  }
}
